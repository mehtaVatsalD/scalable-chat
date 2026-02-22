package com.commoncoder.scalable_chat.controller;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.service.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final MessageRouter messageRouter;

    public ChatController(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }

    @MessageMapping("/chat")
    public void handleChatMessage(@Payload ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String senderId = (String) headerAccessor.getSessionAttributes().get("userId");

        if (senderId != null) {
            message.setSenderId(senderId);
            log.info("Received message from user {}: {}", senderId, message.getContent());
            messageRouter.route(message);
        } else {
            log.error("Received message from unauthenticated session!");
        }
    }
}
