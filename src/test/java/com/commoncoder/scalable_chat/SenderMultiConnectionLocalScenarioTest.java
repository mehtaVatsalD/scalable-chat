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
 * Verifies that when a SENDER has multiple connections on the SAME server, all their connections
 * receive the message they just sent.
 */
public class SenderMultiConnectionLocalScenarioTest {

  private static final Logger log =
      LoggerFactory.getLogger(SenderMultiConnectionLocalScenarioTest.class);
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
  void testSenderReceivesOwnMessageOnAllLocalConnections() throws Exception {
    log.info("=== Sender Multi-Connection Local Test ===");

    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");
    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";

    try {
      BlockingQueue<ChatMessageData> senderConn1Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> senderConn2Messages = new LinkedBlockingQueue<>();
      BlockingQueue<ChatMessageData> receiverMessages = new LinkedBlockingQueue<>();

      StompSession sessionA1 = TestStompUtils.connectStomp(wsUrl, SENDER, senderConn1Messages);
      @SuppressWarnings("unused")
      StompSession sessionA2 = TestStompUtils.connectStomp(wsUrl, SENDER, senderConn2Messages);
      @SuppressWarnings("unused")
      StompSession sessionB = TestStompUtils.connectStomp(wsUrl, RECEIVER, receiverMessages);

      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .receiverIds(Collections.singletonList(RECEIVER))
              .content("Hello from Connection 1!")
              .build();

      log.info("Sender (Connection 1) sending message...");
      sessionA1.send("/app/message/new", request);

      // 1. Verify Originating Connection (A1) receives it
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn1Messages.isEmpty());
      assertEquals("Hello from Connection 1!", senderConn1Messages.poll().getContent());
      log.info("SUCCESS: Originating connection received message.");

      // 2. Verify Secondary Connection (A2) of same user receives it
      await().atMost(Duration.ofSeconds(5)).until(() -> !senderConn2Messages.isEmpty());
      assertEquals("Hello from Connection 1!", senderConn2Messages.poll().getContent());
      log.info("SUCCESS: Secondary connection of sender received message.");

      // 3. Verify Receiver gets it
      await().atMost(Duration.ofSeconds(5)).until(() -> !receiverMessages.isEmpty());
      assertEquals("Hello from Connection 1!", receiverMessages.poll().getContent());
      log.info("SUCCESS: Intended receiver received message.");

    } finally {
      server.close();
    }
  }
}
