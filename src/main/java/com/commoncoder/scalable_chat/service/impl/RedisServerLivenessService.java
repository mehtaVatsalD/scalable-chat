package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.service.ServerLivenessService;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisServerLivenessService implements ServerLivenessService {

  private final StringRedisTemplate redisTemplate;

  public RedisServerLivenessService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean isServerAlive(String serverId) {
    String key = RedisKeyUtils.getServerInfoKey(serverId);
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }

  @Override
  public void updateSelfLiveness(String serverId, String topicName) {
    String key = RedisKeyUtils.getServerInfoKey(serverId);
    redisTemplate.opsForValue().set(key, topicName, 30, TimeUnit.SECONDS);
  }
}
