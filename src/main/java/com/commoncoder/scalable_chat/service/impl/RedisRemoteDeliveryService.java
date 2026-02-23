package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.InterNodeChatMessage;
import com.commoncoder.scalable_chat.service.RemoteDeliveryService;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRemoteDeliveryService implements RemoteDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(RedisRemoteDeliveryService.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public RedisRemoteDeliveryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void deliverRemote(String targetServerId, InterNodeChatMessage message) {
    String topic = RedisKeyUtils.getServerTopicName(targetServerId);
    try {
      String payload = objectMapper.writeValueAsString(message);
      redisTemplate.convertAndSend(topic, payload);
      log.info("Published to Redis topic {}: {}", topic, payload);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize InterNodeChatMessage for remote delivery", e);
      throw new RuntimeException("Serialization failure", e);
    }
  }
}
