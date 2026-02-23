package com.commoncoder.scalable_chat.config;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.service.NodeIdProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class ServerConfig {

  @Bean
  public ServerMetadata serverMetadata(NodeIdProvider nodeIdProvider) {
    int nodeId = nodeIdProvider.getNodeId();
    String serverId = "chat-api-server-" + nodeId + "-" + UUID.randomUUID().toString();
    return ServerMetadata.builder().serverId(serverId).nodeId(nodeId).build();
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    return container;
  }
}
