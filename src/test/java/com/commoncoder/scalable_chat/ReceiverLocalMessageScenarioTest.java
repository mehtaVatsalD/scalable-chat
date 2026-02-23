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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
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
 * Integration test for Local Message Passing. Verifies that when two users are on the same server,
 * messages are delivered directly without hitting Redis Pub/Sub.
 */
public class ReceiverLocalMessageScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(ReceiverLocalMessageScenarioTest.class);
  private static final String USER_A = "userA";
  private static final String USER_B = "userB";
  private static final String USER_C = "userC";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private ObjectMapper objectMapper = new ObjectMapper();
  private RedisClient nativeRedisClient;

  @BeforeEach
  void setup() {
    // Standard Spring Redis setup
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.afterPropertiesSet();

    // Native client for reliable monitoring
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
  void testOneToOneLocalMessage() throws Exception {
    log.info("=== 1-to-1 Local Message Passing ===");

    // 1. Boot Server
    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // 2. Seed DB with Chat and Participants
    ChatRepository chatRepo = server.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = server.getBean(ChatParticipantRepository.class);

    long chatId = 101L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.ONE_TO_ONE)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(1L)
            .chatId(chatId)
            .userId(USER_A)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(2L)
            .chatId(chatId)
            .userId(USER_B)
            .joinedAt(System.currentTimeMillis())
            .build());

    String serverId = server.getBean(ServerMetadata.class).getServerId();
    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";
    String serverTopic = RedisKeyUtils.getServerTopicName(serverId);

    try {
      // 3. Setup Redis Monitor
      AtomicBoolean redisMessageReceived = new AtomicBoolean(false);
      AtomicBoolean monitorWarmedUp = new AtomicBoolean(false);

      StatefulRedisPubSubConnection<String, String> pubSubConn = nativeRedisClient.connectPubSub();
      pubSubConn.addListener(
          new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
              if (message.contains("WARMUP_SIGNAL")) {
                monitorWarmedUp.set(true);
              } else {
                log.warn("REDIS MONITOR: Unexpected message on {}: {}", channel, message);
                redisMessageReceived.set(true);
              }
            }
          });
      pubSubConn.sync().subscribe(serverTopic);

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
                redisTemplate.convertAndSend(serverTopic, warmupJson);
                return monitorWarmedUp.get();
              });

      // 4. Connect Clients
      BlockingQueue<ChatMessageData> userBMessages = new LinkedBlockingQueue<>();
      StompSession sessionA = TestStompUtils.connectStomp(wsUrl, USER_A, null);
      TestStompUtils.connectStomp(wsUrl, USER_B, userBMessages);

      // 5. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello User B, 1-to-1!")
              .build();
      sessionA.send("/app/message/new", request);

      // 6. Verify local delivery
      await().atMost(Duration.ofSeconds(5)).until(() -> !userBMessages.isEmpty());
      ChatMessageData received = userBMessages.poll();
      assertNotNull(received);
      assertEquals("Hello User B, 1-to-1!", received.getContent());
      assertEquals(chatId, received.getChatId());

      // 7. Verify NO Redis traffic
      Awaitility.await()
          .during(Duration.ofMillis(1000))
          .atMost(Duration.ofMillis(2000))
          .until(() -> !redisMessageReceived.get());
      pubSubConn.close();
    } finally {
      server.close();
    }
  }

  @Test
  void testGroupLocalMessage() throws Exception {
    log.info("=== Group Local Message Passing ===");

    // 1. Boot Server
    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // 2. Seed DB
    ChatRepository chatRepo = server.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = server.getBean(ChatParticipantRepository.class);

    long chatId = 102L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.GROUP)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(3L)
            .chatId(chatId)
            .userId(USER_A)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(4L)
            .chatId(chatId)
            .userId(USER_B)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(5L)
            .chatId(chatId)
            .userId(USER_C)
            .joinedAt(System.currentTimeMillis())
            .build());

    String serverId = server.getBean(ServerMetadata.class).getServerId();
    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";
    String serverTopic = RedisKeyUtils.getServerTopicName(serverId);

    try {
      // 3. Setup Redis Monitor
      AtomicBoolean redisMessageReceived = new AtomicBoolean(false);
      AtomicBoolean monitorWarmedUp = new AtomicBoolean(false);

      StatefulRedisPubSubConnection<String, String> pubSubConn = nativeRedisClient.connectPubSub();
      pubSubConn.addListener(
          new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
              if (message.contains("WARMUP_SIGNAL")) {
                monitorWarmedUp.set(true);
              } else {
                log.warn("REDIS MONITOR: Unexpected message on {}: {}", channel, message);
                redisMessageReceived.set(true);
              }
            }
          });
      pubSubConn.sync().subscribe(serverTopic);

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
                redisTemplate.convertAndSend(serverTopic, warmupJson);
                return monitorWarmedUp.get();
              });

      // 4. Connect Clients
      BlockingQueue<ChatMessageData> userBMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> userCMessages = new LinkedBlockingQueue<>();

      StompSession sessionA = TestStompUtils.connectStomp(wsUrl, USER_A, null);
      TestStompUtils.connectStomp(wsUrl, USER_B, userBMessages);
      TestStompUtils.connectStomp(wsUrl, USER_C, userCMessages);

      // 5. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("Hello Group members!")
              .build();
      sessionA.send("/app/message/new", request);

      // 6. Verify local delivery to BOTH members
      await().atMost(Duration.ofSeconds(5)).until(() -> !userBMessages.isEmpty());
      await().atMost(Duration.ofSeconds(5)).until(() -> !userCMessages.isEmpty());

      ChatMessageData receivedB = userBMessages.poll();
      ChatMessageData receivedC = userCMessages.poll();

      assertNotNull(receivedB);
      assertNotNull(receivedC);
      assertEquals("Hello Group members!", receivedB.getContent());
      assertEquals("Hello Group members!", receivedC.getContent());
      assertEquals(chatId, receivedB.getChatId());
      assertEquals(chatId, receivedC.getChatId());

      // 7. Verify NO Redis traffic
      Awaitility.await()
          .during(Duration.ofMillis(1000))
          .atMost(Duration.ofMillis(2000))
          .until(() -> !redisMessageReceived.get());
      pubSubConn.close();
    } finally {
      server.close();
    }
  }
}
