package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.service.RoutingTable;
import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisRoutingTable implements RoutingTable {

  private final StringRedisTemplate redisTemplate;

  private static final String DECREMENT_DELETE_SCRIPT =
      "local count = redis.call('HINCRBY', KEYS[1], ARGV[1], -1) "
          + "if tonumber(count) <= 0 then "
          + "redis.call('HDEL', KEYS[1], ARGV[1]) "
          + "end "
          + "return count";

  private final RedisScript<Long> disconnectScript =
      new DefaultRedisScript<>(DECREMENT_DELETE_SCRIPT, Long.class);

  public RedisRoutingTable(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void registerConnection(String userId, String serverId) {
    String key = RedisKeyUtils.getUserRoutingKey(userId);
    redisTemplate.opsForHash().increment(key, serverId, 1);
  }

  @Override
  public void deregisterConnection(String userId, String serverId) {
    String key = RedisKeyUtils.getUserRoutingKey(userId);
    redisTemplate.execute(disconnectScript, Collections.singletonList(key), serverId);
  }

  @Override
  public Map<String, Integer> getConnectedServers(String userId) {
    String key = RedisKeyUtils.getUserRoutingKey(userId);
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

    return entries.entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> (String) e.getKey(), e -> Integer.parseInt((String) e.getValue())));
  }

  @Override
  public void removeServerEntry(String userId, String serverId) {
    String key = RedisKeyUtils.getUserRoutingKey(userId);
    redisTemplate.opsForHash().delete(key, serverId);
  }
}
