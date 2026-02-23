package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commoncoder.scalable_chat.entity.Chat;
import com.commoncoder.scalable_chat.entity.ChatParticipant;
import com.commoncoder.scalable_chat.enums.ChatType;
import com.commoncoder.scalable_chat.enums.MessageStatus;
import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.repository.ChatParticipantRepository;
import com.commoncoder.scalable_chat.repository.ChatRepository;
import com.commoncoder.scalable_chat.util.TestStompUtils;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * Verifies that when a SENDER has connections on DIFFERENT servers, all their remote connections
 * receive the message via Redis relay.
 */
public class SenderMultiConnectionRemoteScenarioTest {

  private static final Logger log =
      LoggerFactory.getLogger(SenderMultiConnectionRemoteScenarioTest.class);
  private static final String SENDER = "userSender";
  private static final String RECEIVER_B = "userReceiverB";
  private static final String RECEIVER_C = "userReceiverC";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RedisClient nativeRedisClient;

  @BeforeEach
  void setup() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.afterPropertiesSet();

    nativeRedisClient = RedisClient.create("redis://localhost:6379");

    log.info("Flushing Redis before test...");
    redisTemplate.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @AfterEach
  void teardown() {
    if (connectionFactory != null) connectionFactory.destroy();
    if (nativeRedisClient != null) nativeRedisClient.shutdown();
  }

  @Test
  void testOneToOneSenderRemoteMultiConnection() throws Exception {
    log.info("=== 1-to-1 Sender Multi-Connection Remote Test (Cross-Server) ===");

    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // Seed DB
    ChatRepository chatRepoA = serverA.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepoA = serverA.getBean(ChatParticipantRepository.class);

    long chatId = 401L;
    chatRepoA.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.ONE_TO_ONE)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepoA.save(
        ChatParticipant.builder()
            .id(41L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepoA.save(
        ChatParticipant.builder()
            .id(42L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());

    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderOnAMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderOnBMessages = new LinkedBlockingQueue<>();

      // SENDER connects to A and B
      StompSession sessionSenderA = TestStompUtils.connectStomp(wsUrlA, SENDER, senderOnAMessages);
      TestStompUtils.connectStomp(wsUrlB, SENDER, senderOnBMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello 1-to-1 Cross-Server!")
              .build();

      log.info("Sender (on Server A) sending message...");
      sessionSenderA.send("/app/message/new", request);

      // 6. Verify all
      // Sender on A should get 2
      await().atMost(Duration.ofSeconds(5)).until(() -> senderOnAMessages.size() == 2);
      // Sender on B should also get 2 (relayed via Redis)
      await().atMost(Duration.ofSeconds(5)).until(() -> senderOnBMessages.size() == 2);

      // Check Sender A
      ChatMessageData aDraft = senderOnAMessages.poll();
      assertNotNull(aDraft.getMessageId());
      assertNotNull(aDraft.getTimestamp());
      assertEquals(chatId, aDraft.getChatId());
      assertEquals(SENDER, aDraft.getSenderId());
      assertEquals("Hello 1-to-1 Cross-Server!", aDraft.getContent());
      assertEquals(MessageStatus.DRAFT, aDraft.getStatus());

      ChatMessageData aPublished = senderOnAMessages.poll();
      assertNotNull(aPublished.getMessageId());
      assertNotNull(aPublished.getTimestamp());
      assertEquals(chatId, aPublished.getChatId());
      assertEquals(SENDER, aPublished.getSenderId());
      assertEquals("Hello 1-to-1 Cross-Server!", aPublished.getContent());
      assertEquals(MessageStatus.PUBLISHED, aPublished.getStatus());

      // Check Sender B
      ChatMessageData bDraft = senderOnBMessages.poll();
      assertNotNull(bDraft.getMessageId());
      assertNotNull(bDraft.getTimestamp());
      assertEquals(chatId, bDraft.getChatId());
      assertEquals(SENDER, bDraft.getSenderId());
      assertEquals("Hello 1-to-1 Cross-Server!", bDraft.getContent());
      assertEquals(MessageStatus.DRAFT, bDraft.getStatus());

      ChatMessageData bPublished = senderOnBMessages.poll();
      assertNotNull(bPublished.getMessageId());
      assertNotNull(bPublished.getTimestamp());
      assertEquals(chatId, bPublished.getChatId());
      assertEquals(SENDER, bPublished.getSenderId());
      assertEquals("Hello 1-to-1 Cross-Server!", bPublished.getContent());
      assertEquals(MessageStatus.PUBLISHED, bPublished.getStatus());

    } finally {
      serverA.close();
      serverB.close();
    }
  }

  @Test
  void testGroupSenderRemoteMultiConnection() throws Exception {
    log.info("=== Group Sender Multi-Connection Remote Test (Cross-Server) ===");

    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // Seed DB
    ChatRepository chatRepoA = serverA.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepoA = serverA.getBean(ChatParticipantRepository.class);

    long chatId = 402L;
    chatRepoA.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.GROUP)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepoA.save(
        ChatParticipant.builder()
            .id(43L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepoA.save(
        ChatParticipant.builder()
            .id(44L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepoA.save(
        ChatParticipant.builder()
            .id(45L)
            .chatId(chatId)
            .userId(RECEIVER_C)
            .joinedAt(System.currentTimeMillis())
            .build());

    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderOnAMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderOnBMessages = new LinkedBlockingQueue<>();

      // SENDER connects to A and B
      StompSession sessionSenderA = TestStompUtils.connectStomp(wsUrlA, SENDER, senderOnAMessages);
      TestStompUtils.connectStomp(wsUrlB, SENDER, senderOnBMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello Group Cross-Server!")
              .build();

      log.info("Sender (on Server A) sending group message...");
      sessionSenderA.send("/app/message/new", request);

      // 6. Verify all
      await().atMost(Duration.ofSeconds(5)).until(() -> senderOnAMessages.size() == 2);
      await().atMost(Duration.ofSeconds(5)).until(() -> senderOnBMessages.size() == 2);

      // Check Sender A
      ChatMessageData aDraft = senderOnAMessages.poll();
      assertNotNull(aDraft.getMessageId());
      assertNotNull(aDraft.getTimestamp());
      assertEquals(chatId, aDraft.getChatId());
      assertEquals(SENDER, aDraft.getSenderId());
      assertEquals("Hello Group Cross-Server!", aDraft.getContent());
      assertEquals(MessageStatus.DRAFT, aDraft.getStatus());

      ChatMessageData aPublished = senderOnAMessages.poll();
      assertNotNull(aPublished.getMessageId());
      assertNotNull(aPublished.getTimestamp());
      assertEquals(chatId, aPublished.getChatId());
      assertEquals(SENDER, aPublished.getSenderId());
      assertEquals("Hello Group Cross-Server!", aPublished.getContent());
      assertEquals(MessageStatus.PUBLISHED, aPublished.getStatus());

      // Check Sender B
      ChatMessageData bDraft = senderOnBMessages.poll();
      assertNotNull(bDraft.getMessageId());
      assertNotNull(bDraft.getTimestamp());
      assertEquals(chatId, bDraft.getChatId());
      assertEquals(SENDER, bDraft.getSenderId());
      assertEquals("Hello Group Cross-Server!", bDraft.getContent());
      assertEquals(MessageStatus.DRAFT, bDraft.getStatus());

      ChatMessageData bPublished = senderOnBMessages.poll();
      assertNotNull(bPublished.getMessageId());
      assertNotNull(bPublished.getTimestamp());
      assertEquals(chatId, bPublished.getChatId());
      assertEquals(SENDER, bPublished.getSenderId());
      assertEquals("Hello Group Cross-Server!", bPublished.getContent());
      assertEquals(MessageStatus.PUBLISHED, bPublished.getStatus());

    } finally {
      serverA.close();
      serverB.close();
    }
  }
}
