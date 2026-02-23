package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.commoncoder.scalable_chat.entity.Chat;
import com.commoncoder.scalable_chat.entity.ChatParticipant;
import com.commoncoder.scalable_chat.enums.ChatType;
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
 * Verifies that when a SENDER has multiple connections on the SAME server, all their connections
 * receive the message they just sent.
 */
public class SenderMultiConnectionLocalScenarioTest {

  private static final Logger log =
      LoggerFactory.getLogger(SenderMultiConnectionLocalScenarioTest.class);
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
  void testOneToOneSenderMultiConnection() throws Exception {
    log.info("=== 1-to-1 Sender Multi-Connection Local Test ===");

    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // Seed DB
    ChatRepository chatRepo = server.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = server.getBean(ChatParticipantRepository.class);

    long chatId = 301L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.ONE_TO_ONE)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(31L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(32L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());

    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderConn1Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderConn2Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverMessages = new LinkedBlockingQueue<>();

      StompSession sessionA1 = TestStompUtils.connectStomp(wsUrl, SENDER, senderConn1Messages);
      TestStompUtils.connectStomp(wsUrl, SENDER, senderConn2Messages);
      TestStompUtils.connectStomp(wsUrl, RECEIVER_B, receiverMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello 1-to-1 from Conn 1!")
              .build();

      log.info("Sender (Conn 1) sending message...");
      sessionA1.send("/app/message/new", request);

      // Verify all
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn1Messages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn2Messages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverMessages.isEmpty());

      assertEquals("Hello 1-to-1 from Conn 1!", senderConn1Messages.poll().getContent());
      assertEquals("Hello 1-to-1 from Conn 1!", senderConn2Messages.poll().getContent());
      assertEquals("Hello 1-to-1 from Conn 1!", receiverMessages.poll().getContent());

    } finally {
      server.close();
    }
  }

  @Test
  void testGroupSenderMultiConnection() throws Exception {
    log.info("=== Group Sender Multi-Connection Local Test ===");

    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // Seed DB
    ChatRepository chatRepo = server.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = server.getBean(ChatParticipantRepository.class);

    long chatId = 302L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.GROUP)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(33L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(34L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(35L)
            .chatId(chatId)
            .userId(RECEIVER_C)
            .joinedAt(System.currentTimeMillis())
            .build());

    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderConn1Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderConn2Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverBMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverCMessages = new LinkedBlockingQueue<>();

      StompSession sessionA1 = TestStompUtils.connectStomp(wsUrl, SENDER, senderConn1Messages);
      TestStompUtils.connectStomp(wsUrl, SENDER, senderConn2Messages);
      TestStompUtils.connectStomp(wsUrl, RECEIVER_B, receiverBMessages);
      TestStompUtils.connectStomp(wsUrl, RECEIVER_C, receiverCMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello Group from Conn 1!")
              .build();

      log.info("Sender (Conn 1) sending group message...");
      sessionA1.send("/app/message/new", request);

      // Verify all
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn1Messages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn2Messages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverBMessages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverCMessages.isEmpty());

      assertEquals("Hello Group from Conn 1!", senderConn1Messages.poll().getContent());
      assertEquals("Hello Group from Conn 1!", senderConn2Messages.poll().getContent());
      assertEquals("Hello Group from Conn 1!", receiverBMessages.poll().getContent());
      assertEquals("Hello Group from Conn 1!", receiverCMessages.poll().getContent());

    } finally {
      server.close();
    }
  }
}
