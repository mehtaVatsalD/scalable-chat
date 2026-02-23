package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.ClientDeliveryMessage;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Integration test for Local Message Passing. Verifies that when two users are on the same server,
 * messages are delivered directly without hitting Redis Pub/Sub.
 */
public class LocalMessageScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(LocalMessageScenarioTest.class);
  private static final String USER_A = "userA";
  private static final String USER_B = "userB";

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
  void testLocalMessageDeliveryFlow() throws Exception {
    log.info("=== Local Message Passing Flow ===");

    // 1. Boot Server
    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    String serverId = server.getBean(ServerMetadata.class).getServerId();
    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";
    String serverTopic = RedisKeyUtils.getServerTopicName(serverId);

    try {
      // 2. Setup Redis Monitor using native Lettuce connection
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

      // Warm up monitor
      ClientDeliverableData<String> warmupMsg =
          ClientDeliverableData.<String>builder()
              .channelId("warmup")
              .data("WARMUP_SIGNAL")
              .receiverUserIds(Collections.singletonList("system"))
              .build();
      String warmupJson = objectMapper.writeValueAsString(warmupMsg);

      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () -> {
                redisTemplate.convertAndSend(serverTopic, warmupJson);
                return monitorWarmedUp.get();
              });
      log.info("Redis Monitor is active and warmed up.");

      // 3. Connect Clients
      BlockingQueue<ClientDeliveryMessage> userBMessages = new LinkedBlockingQueue<>();
      StompSession sessionA = connectStomp(wsUrl, USER_A, null);
      @SuppressWarnings("unused")
      StompSession ignoreSessionB = connectStomp(wsUrl, USER_B, userBMessages);

      // 4. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .receiverIds(Collections.singletonList(USER_B))
              .content("Hello User B, directly from A!")
              .build();

      log.info("User A sending local message to User B...");
      sessionA.send("/app/message/new", request);

      // 5. Verify local delivery
      await().atMost(Duration.ofSeconds(5)).until(() -> !userBMessages.isEmpty());
      ClientDeliveryMessage received = userBMessages.poll();
      assertNotNull(received);
      assertEquals("Hello User B, directly from A!", received.getContent());
      log.info("STEP 1 PASSED: User B received message locally.");

      // 6. Verify NO Redis traffic
      Awaitility.await()
          .during(Duration.ofMillis(1000))
          .atMost(Duration.ofMillis(2000))
          .until(() -> !redisMessageReceived.get());
      log.info("STEP 2 PASSED: Verified NO message was sent via Redis Pub/Sub.");

      pubSubConn.close();

    } finally {
      server.close();
    }

    log.info("=== Local Message Passing Flow: COMPLETE ===");
  }

  private StompSession connectStomp(
      String url, String userId, BlockingQueue<ClientDeliveryMessage> messageQueue)
      throws Exception {
    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("userId", userId);

    StompSessionHandler sessionHandler =
        new StompSessionHandlerAdapter() {
          @Override
          public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            if (messageQueue != null) {
              session.subscribe(
                  "/user/queue/messages",
                  new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                      return ClientDeliveryMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                      log.info("User {} received message: {}", userId, payload);
                      messageQueue.add((ClientDeliveryMessage) payload);
                    }
                  });
            }
          }
        };

    return stompClient
        .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, sessionHandler)
        .get(10, TimeUnit.SECONDS);
  }
}
