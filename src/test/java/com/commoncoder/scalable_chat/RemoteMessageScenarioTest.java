package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Integration test for Remote (Cross-Server) Message Passing. Verifies that when users are on
 * different servers: 1. The message is published to the target server's Redis topic. 2. The target
 * server receives it and delivers it to the end-user via WebSocket.
 */
public class RemoteMessageScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(RemoteMessageScenarioTest.class);
  private static final String SENDER = "userSender";
  private static final String RECEIVER = "userReceiver";

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
  void testRemoteMessageDeliveryFlow() throws Exception {
    log.info("=== Remote Message Passing Flow (Cross-Server) ===");

    // 1. Boot Server A and Server B
    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    String serverBId = serverB.getBean(ServerMetadata.class).getServerId();
    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";
    String serverBTopic = RedisKeyUtils.getServerTopicName(serverBId);

    try {
      // 2. Setup Redis Monitor on Server B's topic
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
                  log.info("REDIS MONITOR: Captured inter-node message: {}", message);
                  ClientDeliverableData<ChatMessageData> deliverable =
                      objectMapper.readValue(
                          message, new TypeReference<ClientDeliverableData<ChatMessageData>>() {});
                  capturedRedisMsg.set(deliverable);
                }
              } catch (Exception e) {
                log.error("Failed to parse Redis message: " + message);
              }
            }
          });
      pubSubConn.sync().subscribe(serverBTopic);

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
                redisTemplate.convertAndSend(serverBTopic, warmupJson);
                return monitorWarmedUp.get();
              });
      log.info("Redis Monitor on Server B topic is ready.");

      // 3. Connect Sender to Server A, Receiver to Server B
      BlockingQueue<ChatMessageData> receiverMessages = new LinkedBlockingQueue<>();
      StompSession sessionSender = connectStomp(wsUrlA, SENDER, null);
      @SuppressWarnings("unused")
      StompSession ignoreSessionReceiver = connectStomp(wsUrlB, RECEIVER, receiverMessages);

      // 4. Sender (Server A) sends message to Receiver (Server B)
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .receiverIds(Collections.singletonList(RECEIVER))
              .content("Hello across servers!")
              .build();

      log.info("Sender (Server A) sending remote message to Receiver (Server B)...");
      sessionSender.send("/app/message/new", request);

      // 5. Verify it went through Redis
      await().atMost(Duration.ofSeconds(5)).until(() -> capturedRedisMsg.get() != null);
      ClientDeliverableData<ChatMessageData> redisMsg = capturedRedisMsg.get();
      assertNotNull(redisMsg.getData());
      assertEquals(SENDER, redisMsg.getData().getSenderId());
      assertEquals(RECEIVER, redisMsg.getReceiverUserIds().get(0));
      assertEquals("Hello across servers!", redisMsg.getData().getContent());
      log.info("STEP 1 PASSED: Verified message was published to Redis topic of Server B.");

      // 6. Verify it was delivered to Receiver via Server B's WebSocket
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverMessages.isEmpty());
      ChatMessageData finalMsg = receiverMessages.poll();
      assertNotNull(finalMsg);
      assertEquals(SENDER, finalMsg.getSenderId());
      assertEquals("Hello across servers!", finalMsg.getContent());
      log.info("STEP 2 PASSED: Receiver (on Server B) received the message via WebSocket.");

      pubSubConn.close();

    } finally {
      serverA.close();
      serverB.close();
    }

    log.info("=== Remote Message Passing Flow: COMPLETE ===");
  }

  private StompSession connectStomp(
      String url, String userId, BlockingQueue<ChatMessageData> messageQueue) throws Exception {
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
                      return ChatMessageData.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                      log.info("User {} received message: {}", userId, payload);
                      messageQueue.add((ChatMessageData) payload);
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
