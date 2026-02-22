package com.commoncoder.scalable_chat;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
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
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Plain JUnit test (no @SpringBootTest).
 * The test is a pure observer — it connects to Redis directly and
 * spawns child Spring Boot contexts as the servers under test.
 *
 * Uses Awaitility instead of Thread.sleep() to avoid flaky tests — each
 * assertion actively polls Redis until the expected state arrives or times out.
 */
public class UserRoutingScenarioTest {

    private static final Logger log = LoggerFactory.getLogger(UserRoutingScenarioTest.class);
    private static final String TEST_USER = "testUser1";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(3);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setup() {
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
    void teardown() {
        connectionFactory.destroy();
    }

    @Test
    void testUserRoutingCountsOnConnectAndDisconnect() throws Exception {
        log.info("=== User Routing Table Tracking ===");
        String routingKey = RedisKeyUtils.getUserRoutingKey(TEST_USER);

        // --- Boot Server A ---
        ConfigurableApplicationContext serverA = SpringApplication.run(ScalableChatApplication.class,
                "--server.port=0");
        String serverAId = serverA.getBean(ServerMetadata.class).getServerId();
        int portA = getPort(serverA);
        String wsUrlA = "ws://localhost:" + portA + "/chat-ws-native";
        log.info("Server A started. ID={}, port={}", serverAId, portA);

        try {
            // --- Client 1 connects to Server A ---
            StompSession client1 = connectStomp(wsUrlA, TEST_USER);
            await().atMost(WAIT_TIMEOUT)
                    .until(() -> countForServer(redisTemplate.opsForHash().entries(routingKey), serverAId) == 1);
            log.info("STEP 1 PASSED: 1 connection on Server A → {}", redisTemplate.opsForHash().entries(routingKey));

            // --- Client 2 connects to Server A (same user, same server) ---
            StompSession client2 = connectStomp(wsUrlA, TEST_USER);
            await().atMost(WAIT_TIMEOUT)
                    .until(() -> countForServer(redisTemplate.opsForHash().entries(routingKey), serverAId) == 2);
            log.info("STEP 2 PASSED: 2 connections on Server A → {}", redisTemplate.opsForHash().entries(routingKey));

            // --- Boot Server B and connect same user ---
            ConfigurableApplicationContext serverB = SpringApplication.run(ScalableChatApplication.class,
                    "--server.port=0");
            String serverBId = serverB.getBean(ServerMetadata.class).getServerId();
            int portB = getPort(serverB);
            String wsUrlB = "ws://localhost:" + portB + "/chat-ws-native";
            log.info("Server B started. ID={}, port={}", serverBId, portB);

            try {
                StompSession client3 = connectStomp(wsUrlB, TEST_USER);
                await().atMost(WAIT_TIMEOUT)
                        .until(() -> countForServer(redisTemplate.opsForHash().entries(routingKey), serverAId) == 2 &&
                                countForServer(redisTemplate.opsForHash().entries(routingKey), serverBId) == 1);
                log.info("STEP 3 PASSED: 2 on Server A, 1 on Server B → {}",
                        redisTemplate.opsForHash().entries(routingKey));

                // --- Disconnect client1 from Server A ---
                client1.disconnect();
                await().atMost(WAIT_TIMEOUT)
                        .until(() -> countForServer(redisTemplate.opsForHash().entries(routingKey), serverAId) == 1);
                log.info("STEP 4 PASSED: Server A decremented to 1 → {}",
                        redisTemplate.opsForHash().entries(routingKey));

                // --- Disconnect client2 from Server A (count hits 0 → entry removed) ---
                client2.disconnect();
                await().atMost(WAIT_TIMEOUT)
                        .until(() -> !redisTemplate.opsForHash().entries(routingKey).containsKey(serverAId));
                assertEquals(1, countForServer(redisTemplate.opsForHash().entries(routingKey), serverBId),
                        "Server B count should still be 1");
                log.info("STEP 5 PASSED: Server A entry removed, Server B still has 1 → {}",
                        redisTemplate.opsForHash().entries(routingKey));

                // --- Disconnect client3 from Server B ---
                client3.disconnect();
                await().atMost(WAIT_TIMEOUT).until(() -> redisTemplate.opsForHash().entries(routingKey).isEmpty());
                log.info("STEP 6 PASSED: Routing table is empty → {}", redisTemplate.opsForHash().entries(routingKey));

            } finally {
                serverB.close();
            }

        } finally {
            serverA.close();
        }

        log.info("=== User Routing Table Tracking: COMPLETE ===");
    }

    private StompSession connectStomp(String url, String userId) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userId", userId);

        StompSession session = stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);

        log.info("Client connected to {} as user '{}'", url, userId);
        return session;
    }

    private int countForServer(Map<Object, Object> routing, String serverId) {
        Object val = routing.get(serverId);
        return val == null ? 0 : Integer.parseInt(val.toString());
    }

    private int getPort(ConfigurableApplicationContext ctx) {
        return Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
    }
}
