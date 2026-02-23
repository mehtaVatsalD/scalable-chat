package com.commoncoder.scalable_chat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Autowired private UserInterceptor userInterceptor;

  @Bean
  public TaskScheduler heartbeatScheduler() {
    return new ThreadPoolTaskScheduler();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(userInterceptor);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // Prefixes for messages going FROM server TO client
    // /topic for broadcasts, /queue for private direct messages
    config
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {15000, 15000})
        .setTaskScheduler(heartbeatScheduler());

    // Prefix for messages going FROM client TO server (handled by @MessageMapping)
    config.setApplicationDestinationPrefixes("/app");

    // Prefix for user-specific routing (e.g., /user/queue/messages)
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // SockJS endpoint for browser clients
    registry.addEndpoint("/chat-ws").setAllowedOrigins("*").withSockJS();

    // Plain WebSocket endpoint for native clients (tests, non-browser)
    registry.addEndpoint("/chat-ws-native").setAllowedOrigins("*");
  }
}
