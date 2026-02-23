package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.InterNodeChatMessage;
import com.commoncoder.scalable_chat.service.RemoteMessageReceiver;
import com.commoncoder.scalable_chat.service.RemoteSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

/** Redis-based implementation of RemoteSubscriptionService. */
@Service
public class RedisRemoteSubscriptionService implements RemoteSubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(RedisRemoteSubscriptionService.class);

  private final RedisMessageListenerContainer container;
  private final ObjectMapper objectMapper;

  public RedisRemoteSubscriptionService(
      RedisMessageListenerContainer container, ObjectMapper objectMapper) {
    this.container = container;
    this.objectMapper = objectMapper;
  }

  @Override
  public void subscribe(String channelId, RemoteMessageReceiver receiver) {
    log.info("Subscribing to Redis topic: {}", channelId);

    container.addMessageListener(
        new MessageListener() {
          @Override
          public void onMessage(Message message, byte[] pattern) {
            try {
              // Deserialize the JSON payload from Redis
              InterNodeChatMessage chatMessage =
                  objectMapper.readValue(message.getBody(), InterNodeChatMessage.class);
              receiver.onMessage(chatMessage);
            } catch (IOException e) {
              log.error("Failed to deserialize inter-node message from Redis", e);
            }
          }
        },
        new ChannelTopic(channelId));
  }

  @Override
  public void unsubscribe(String channelId) {
    log.info("Unsubscribing from Redis topic: {}", channelId);
    // Passing null as the first argument removes all listeners for that topic
    container.removeMessageListener(null, new ChannelTopic(channelId));
  }
}
