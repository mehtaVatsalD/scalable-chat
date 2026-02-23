package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commoncoder.scalable_chat.entity.Chat;
import com.commoncoder.scalable_chat.entity.ChatParticipant;
import com.commoncoder.scalable_chat.enums.ChatType;
import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.repository.ChatParticipantRepository;
import com.commoncoder.scalable_chat.repository.ChatRepository;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import com.commoncoder.scalable_chat.util.TestStompUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
 * Integration test for Remote (Cross-Server) Message Passing. Verifies that when users are on
 * different servers: 1. The message is published to the target server's Redis topic. 2. The target
 * server receives it and delivers it to the end-user via WebSocket.
 */
public class ReceiverRemoteMessageScenarioTest {

  private static final Logger log =
      LoggerFactory.getLogger(ReceiverRemoteMessageScenarioTest.class);
  private static final String SENDER = "userSender";
  private static final String RECEIVER_B = "userReceiverB";
  private static final String RECEIVER_C = "userReceiverC";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private ObjectMapper objectMapper = new ObjectMapper();
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
  void testOneToOneRemoteMessage() throws Exception {
    log.info("=== 1-to-1 Remote Message Passing (Cross-Server) ===");

    // 1. Boot Server A and Server B
    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // 2. Seed DB
    ChatRepository chatRepo = serverA.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = serverA.getBean(ChatParticipantRepository.class);

    long chatId = 201L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.ONE_TO_ONE)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(11L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(12L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());

    String serverBId = serverB.getBean(ServerMetadata.class).getServerId();
    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";
    String serverBTopic = RedisKeyUtils.getServerTopicName(serverBId);

    try {
      // 3. Setup Redis Monitor on Server B
      AtomicBoolean monitorWarmedUp = new AtomicBoolean(false);
      AtomicReference<ClientDeliverableData<ChatMessageData>> capturedRedisMsg =
          new AtomicReference<>();

      StatefulRedisPubSubConnection<String, String> pubSubConn = nativeRedisClient.connectPubSub();
      pubSubConn.addListener(
          new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
              try {
                if (message.contains("WARMUP_SIGNAL")) {
                  monitorWarmedUp.set(true);
                } else {
                  capturedRedisMsg.set(objectMapper.readValue(message, new TypeReference<>() {}));
                }
              } catch (Exception e) {
              }
            }
          });
      pubSubConn.sync().subscribe(serverBTopic);

      // Warm up
      ClientDeliverableData<String> warmupMsg =
          ClientDeliverableData.<String>builder()
              .channelId("warmup")
              .data("WARMUP_SIGNAL")
              .receiverUserIds(List.of("system"))
              .build();
      String warmupJson = objectMapper.writeValueAsString(warmupMsg);
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () -> {
                redisTemplate.convertAndSend(serverBTopic, warmupJson);
                return monitorWarmedUp.get();
              });

      // 4. Connect Sender (A) and Receiver (B)
      BlockingQueue<ChatMessageData> receiverBMessages = new LinkedBlockingQueue<>();
      StompSession sessionSender = TestStompUtils.connectStomp(wsUrlA, SENDER, null);
      TestStompUtils.connectStomp(wsUrlB, RECEIVER_B, receiverBMessages);

      // 5. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello across servers 1-to-1!")
              .build();
      sessionSender.send("/app/message/new", request);

      // 6. Verify Redis Pub/Sub
      await().atMost(Duration.ofSeconds(5)).until(() -> capturedRedisMsg.get() != null);
      ClientDeliverableData<ChatMessageData> redisMsg = capturedRedisMsg.get();
      assertEquals(RECEIVER_B, redisMsg.getReceiverUserIds().get(0));
      assertEquals("Hello across servers 1-to-1!", redisMsg.getData().getContent());

      // 7. Verify WebSocket Delivery
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverBMessages.isEmpty());
      ChatMessageData receivedB = receiverBMessages.poll();
      assertNotNull(receivedB);
      assertEquals("Hello across servers 1-to-1!", receivedB.getContent());

      pubSubConn.close();
    } finally {
      serverA.close();
      serverB.close();
    }
  }

  @Test
  void testGroupRemoteMessage() throws Exception {
    log.info("=== Group Remote Message Passing (Cross-Server) ===");

    // 1. Boot Server A and Server B
    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // 2. Seed DB
    ChatRepository chatRepo = serverA.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = serverA.getBean(ChatParticipantRepository.class);

    long chatId = 202L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.GROUP)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(21L)
            .chatId(chatId)
            .userId(SENDER)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(22L)
            .chatId(chatId)
            .userId(RECEIVER_B)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(23L)
            .chatId(chatId)
            .userId(RECEIVER_C)
            .joinedAt(System.currentTimeMillis())
            .build());

    String serverBId = serverB.getBean(ServerMetadata.class).getServerId();
    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";
    String serverBTopic = RedisKeyUtils.getServerTopicName(serverBId);

    try {
      // 3. Setup Redis Monitor on Server B
      AtomicBoolean monitorWarmedUp = new AtomicBoolean(false);
      AtomicReference<ClientDeliverableData<ChatMessageData>> capturedRedisMsg =
          new AtomicReference<>();

      StatefulRedisPubSubConnection<String, String> pubSubConn = nativeRedisClient.connectPubSub();
      pubSubConn.addListener(
          new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
              try {
                if (message.contains("WARMUP_SIGNAL")) {
                  monitorWarmedUp.set(true);
                } else {
                  capturedRedisMsg.set(objectMapper.readValue(message, new TypeReference<>() {}));
                }
              } catch (Exception e) {
              }
            }
          });
      pubSubConn.sync().subscribe(serverBTopic);

      // Warm up
      ClientDeliverableData<String> warmupMsg =
          ClientDeliverableData.<String>builder()
              .channelId("warmup")
              .data("WARMUP_SIGNAL")
              .receiverUserIds(List.of("system"))
              .build();
      String warmupJson = objectMapper.writeValueAsString(warmupMsg);
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () -> {
                redisTemplate.convertAndSend(serverBTopic, warmupJson);
                return monitorWarmedUp.get();
              });

      // 4. Connect Sender (A) and Receivers (both on B)
      BlockingQueue<ChatMessageData> receiverBMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverCMessages = new LinkedBlockingQueue<>();
      StompSession sessionSender = TestStompUtils.connectStomp(wsUrlA, SENDER, null);
      TestStompUtils.connectStomp(wsUrlB, RECEIVER_B, receiverBMessages);
      TestStompUtils.connectStomp(wsUrlB, RECEIVER_C, receiverCMessages);

      // 5. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello Group across servers!")
              .build();
      sessionSender.send("/app/message/new", request);

      // 6. Verify Redis Pub/Sub (should contain both B and C in receiverUserIds)
      await().atMost(Duration.ofSeconds(5)).until(() -> capturedRedisMsg.get() != null);
      ClientDeliverableData<ChatMessageData> redisMsg = capturedRedisMsg.get();
      List<String> recipients = redisMsg.getReceiverUserIds();
      assertEquals(2, recipients.size());
      assert (recipients.contains(RECEIVER_B));
      assert (recipients.contains(RECEIVER_C));
      assertEquals("Hello Group across servers!", redisMsg.getData().getContent());

      // 7. Verify WebSocket Delivery to BOTH
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverBMessages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverCMessages.isEmpty());

      ChatMessageData finalMsgB = receiverBMessages.poll();
      ChatMessageData finalMsgC = receiverCMessages.poll();

      assertNotNull(finalMsgB);
      assertNotNull(finalMsgC);
      assertEquals("Hello Group across servers!", finalMsgB.getContent());
      assertEquals("Hello Group across servers!", finalMsgC.getContent());

      pubSubConn.close();
    } finally {
      serverA.close();
      serverB.close();
    }
  }
}
