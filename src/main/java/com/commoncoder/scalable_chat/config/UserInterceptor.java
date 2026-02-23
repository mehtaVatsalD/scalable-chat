package com.commoncoder.scalable_chat.config;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class UserInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String userId = accessor.getFirstNativeHeader("userId");

      if (userId != null && !userId.trim().isEmpty()) {
        // 1. Add to session attributes
        accessor.getSessionAttributes().put("userId", userId);

        // 2. Set Principal so Spring's SimpUserRegistry and /user/ destinations work.
        accessor.setUser(new UserPrincipal(userId));
      }
    }
    return message;
  }

  private static class UserPrincipal implements Principal {
    private final String name;

    public UserPrincipal(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
