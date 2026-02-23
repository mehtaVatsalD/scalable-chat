package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.util.TestStompUtils;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.util.Collections;
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
  private static final String RECEIVER = "userReceiver";

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
  void testSenderReceivesOwnMessageOnRemoteConnections() throws Exception {
    log.info("=== Sender Multi-Connection Remote Test (Cross-Server) ===");

    ConfigurableApplicationContext serverA =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    ConfigurableApplicationContext serverB =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    int portA = Integer.parseInt(serverA.getEnvironment().getProperty("local.server.port"));
    int portB = Integer.parseInt(serverB.getEnvironment().getProperty("local.server.port"));

    String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
    String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderOnAMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderOnBMessages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverOnBMessages = new LinkedBlockingQueue<>();

      // SENDER connects to A and B
      StompSession sessionSenderA = TestStompUtils.connectStomp(wsUrlA, SENDER, senderOnAMessages);
      @SuppressWarnings("unused")
      StompSession sessionSenderB = TestStompUtils.connectStomp(wsUrlB, SENDER, senderOnBMessages);

      // RECEIVER connects to B
      @SuppressWarnings("unused")
      StompSession sessionReceiverB =
          TestStompUtils.connectStomp(wsUrlB, RECEIVER, receiverOnBMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .receiverIds(Collections.singletonList(RECEIVER))
              .content("Hello from Server A to Server B!")
              .build();

      log.info("Sender (on Server A) sending message...");
      sessionSenderA.send("/app/message/new", request);

      // 1. Verify Sender's Session on Server A (Local) receives it
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderOnAMessages.isEmpty());
      assertEquals("Hello from Server A to Server B!", senderOnAMessages.poll().getContent());
      log.info("SUCCESS: Sender session on Server A received message locally.");

      // 2. Verify Sender's Session on Server B (Remote) receives it via Redis
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderOnBMessages.isEmpty());
      assertEquals("Hello from Server A to Server B!", senderOnBMessages.poll().getContent());
      log.info("SUCCESS: Sender session on Server B received message via Redis relay.");

      // 3. Verify Receiver (on Server B) receives it via Redis
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverOnBMessages.isEmpty());
      assertEquals("Hello from Server A to Server B!", receiverOnBMessages.poll().getContent());
      log.info("SUCCESS: Receiver on Server B received message.");

    } finally {
      serverA.close();
      serverB.close();
    }
  }
}
