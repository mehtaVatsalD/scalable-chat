package com.commoncoder.scalable_chat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.service.MessageRouter;

@Controller
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);
  private final MessageRouter messageRouter;

  public ChatController(MessageRouter messageRouter) {
    this.messageRouter = messageRouter;
  }

  @MessageMapping("/chat")
  public void handleChatMessage(
      @Payload ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    String senderId = (String) headerAccessor.getSessionAttributes().get("userId");

    if (senderId != null) {
      ChatMessage updatedMessage = message.toBuilder().senderId(senderId).build();
      log.info("Received message from user {}: {}", senderId, updatedMessage.getContent());
      messageRouter.route(updatedMessage);
    } else {
      log.error("Received message from unauthenticated session!");
    }
  }
}
