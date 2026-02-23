package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.service.NodeIdProvider;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-based implementation of NodeIdProvider. Uses an atomic counter in Redis to assign
 * sequential node IDs.
 */
@Service
public class RedisNodeIdProvider implements NodeIdProvider {

  private final StringRedisTemplate redisTemplate;
  private Integer cachedNodeId;

  public RedisNodeIdProvider(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public synchronized int getNodeId() {
    if (cachedNodeId == null) {
      // Increment returns the NEW value. If key didn't exist, it returns 1.
      // User asked for 0 if not present, so we subtract 1.
      Long incremented = redisTemplate.opsForValue().increment(RedisKeyUtils.NODE_ID_COUNTER_KEY);
      cachedNodeId = (incremented != null ? incremented.intValue() : 1) - 1;
    }
    return cachedNodeId;
  }
}
