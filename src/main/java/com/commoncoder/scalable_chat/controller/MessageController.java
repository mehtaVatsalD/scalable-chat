package com.commoncoder.scalable_chat.controller;

import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class MessageController {

  private static final Logger log = LoggerFactory.getLogger(MessageController.class);
  private final MessageService messageService;

  public MessageController(MessageService messageService) {
    this.messageService = messageService;
  }

  @MessageMapping("/message/new")
  public void handleSendNewChatMessage(
      @Payload SendNewChatMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
    String senderId = (String) headerAccessor.getSessionAttributes().get("userId");

    if (senderId != null) {
      messageService.processNewMessage(senderId, request);
    } else {
      log.error("Received message from unauthenticated session!");
    }
  }
}
