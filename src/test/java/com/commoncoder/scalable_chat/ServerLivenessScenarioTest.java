package com.commoncoder.scalable_chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
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

/**
 * Plain JUnit test — no @SpringBootTest. This test is NOT itself a server. It connects to Redis
 * directly and spawns child Spring Boot contexts to simulate servers.
 */
public class ServerLivenessScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(ServerLivenessScenarioTest.class);

  // Direct Redis connection for this test observer — no Spring context needed
  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setup() {
    // Connect directly to Redis as a plain client (not a Spring Boot app)
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.afterPropertiesSet();

    log.info("Flushing Redis before test...");
    redisTemplate.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void testServerRegistrationAndExpiration() throws InterruptedException {
    log.info("=== Server Registration & TTL Expiration ===");

    // Start a server node
    ConfigurableApplicationContext serverContext =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    try {
      ServerMetadata metadata = serverContext.getBean(ServerMetadata.class);
      String serverId = metadata.getServerId();
      String serverKey = RedisKeyUtils.getServerInfoKey(serverId);

      log.info("Server started with ID: {}", serverId);
      log.info("Checking Redis key: {}", serverKey);

      // 1. Verify it is registered in Redis (heartbeat key exists)
      assertTrue(redisTemplate.hasKey(serverKey), "Server should be registered in Redis upon boot");
      log.info("STEP 1 PASSED: Server is registered in Redis.");

      // 2. Verify it is subscribed to its inter-node topic
      String topicName = metadata.getTopicName();
      assertTrue(
          isChannelActive(topicName),
          "Server should be actively subscribed to its topic: " + topicName);
      log.info("STEP 2 PASSED: Server is subscribed to topic: {}", topicName);

      // 3. Kill the server
      log.info("Killing server (closing context)...");
      serverContext.close();
      log.info("Server killed. Heartbeats have stopped.");

      // 4. Verify topic is no longer active (unsubscribed)
      assertFalse(isChannelActive(topicName), "Server topic should be unsubscribed after shutdown");
      log.info("STEP 3 PASSED: Topic unsubscribed after server shutdown.");

      // 5. Wait 45s for the 30s TTL to expire
      log.info("Waiting 45 seconds for Redis TTL expiration...");
      Thread.sleep(45_000);

      // 6. Verify the heartbeat key is gone
      assertFalse(
          redisTemplate.hasKey(serverKey),
          "Server entry should have been removed after TTL expiration");
      log.info("STEP 4 PASSED: Server entry expired from Redis after TTL.");
      log.info("=== Server Registration & TTL Expiration: COMPLETE ===");

    } finally {
      if (serverContext.isRunning()) {
        serverContext.close();
      }
    }
  }

  /**
   * Uses Lettuce directly to check if a Redis Pub/Sub channel has active subscribers via the PUBSUB
   * CHANNELS command.
   */
  private boolean isChannelActive(String channelName) {
    try (io.lettuce.core.api.StatefulRedisConnection<String, String> conn =
        ((io.lettuce.core.RedisClient)
                ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                        redisTemplate.getRequiredConnectionFactory())
                    .getNativeClient())
            .connect()) {
      java.util.List<String> channels = conn.sync().pubsubChannels(channelName);
      return channels != null && !channels.isEmpty();
    }
  }
}
