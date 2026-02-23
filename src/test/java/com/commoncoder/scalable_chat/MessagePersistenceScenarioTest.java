package com.commoncoder.scalable_chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commoncoder.scalable_chat.entity.Chat;
import com.commoncoder.scalable_chat.entity.ChatParticipant;
import com.commoncoder.scalable_chat.entity.Message;
import com.commoncoder.scalable_chat.enums.ChatType;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.repository.ChatParticipantRepository;
import com.commoncoder.scalable_chat.repository.ChatRepository;
import com.commoncoder.scalable_chat.repository.MessageRepository;
import com.commoncoder.scalable_chat.util.TestStompUtils;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * Verifies that when a message is sent via WebSocket, it is correctly persisted in the database.
 */
public class MessagePersistenceScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(MessagePersistenceScenarioTest.class);
  private static final String USER_A = "userA";
  private static final String USER_B = "userB";

  @Test
  void testMessagePersistence() throws Exception {
    log.info("=== Message Persistence Scenario Test ===");

    // 1. Boot Server
    ConfigurableApplicationContext server =
        SpringApplication.run(ScalableChatApplication.class, "--server.port=0");

    // Cleanup Redis
    StringRedisTemplate redisTemplate = server.getBean(StringRedisTemplate.class);
    redisTemplate.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();

    // 2. Seed DB
    ChatRepository chatRepo = server.getBean(ChatRepository.class);
    ChatParticipantRepository participantRepo = server.getBean(ChatParticipantRepository.class);
    MessageRepository messageRepo = server.getBean(MessageRepository.class);

    long chatId = 501L;
    chatRepo.save(
        Chat.builder()
            .id(chatId)
            .type(ChatType.ONE_TO_ONE)
            .createdAt(System.currentTimeMillis())
            .lastActivity(System.currentTimeMillis())
            .build());

    participantRepo.save(
        ChatParticipant.builder()
            .id(51L)
            .chatId(chatId)
            .userId(USER_A)
            .joinedAt(System.currentTimeMillis())
            .build());
    participantRepo.save(
        ChatParticipant.builder()
            .id(52L)
            .chatId(chatId)
            .userId(USER_B)
            .joinedAt(System.currentTimeMillis())
            .build());

    int port = Integer.parseInt(server.getEnvironment().getProperty("local.server.port"));
    String wsUrl = "ws://localhost:" + port + "/chat-ws-native";

    try {
      // 3. Connect Sender
      StompSession sessionA = TestStompUtils.connectStomp(wsUrl, USER_A, null);

      // 4. Send Message
      SendNewChatMessageRequest request =
          SendNewChatMessageRequest.builder()
              .chatId(chatId)
              .content("This message should be persisted!")
              .build();

      log.info("Sending message for persistence check...");
      sessionA.send("/app/message/new", request);

      // 5. Verify Persistence
      await().atMost(Duration.ofSeconds(5)).until(() -> messageRepo.count() > 0);

      List<Message> persistedMessages = messageRepo.findAll();
      assertEquals(1, persistedMessages.size());

      Message msg = persistedMessages.get(0);
      assertNotNull(msg.getId());
      assertEquals(chatId, msg.getChatId());
      assertEquals(USER_A, msg.getSenderId());
      assertEquals("This message should be persisted!", msg.getContent());
      assertNotNull(msg.getTimestamp());

    } finally {
      server.close();
    }
  }
}
