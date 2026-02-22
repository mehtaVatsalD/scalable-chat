package com.commoncoder.scalable_chat.config;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.util.UUID;

@Configuration
public class ServerConfig {

    @Bean
    public ServerMetadata serverMetadata() {
        String serverId = "chat-api-server-" + UUID.randomUUID().toString();
        return new ServerMetadata(serverId);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
