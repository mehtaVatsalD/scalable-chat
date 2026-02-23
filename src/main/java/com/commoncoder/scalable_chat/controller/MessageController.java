package com.commoncoder.scalable_chat.controller;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.ClientDeliveryMessage;
import com.commoncoder.scalable_chat.service.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class MessageController {

  private static final Logger log = LoggerFactory.getLogger(MessageController.class);
  private final MessageRouter messageRouter;

  public MessageController(MessageRouter messageRouter) {
    this.messageRouter = messageRouter;
  }

  @MessageMapping("/message")
  public void handleChatMessage(
      @Payload ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    String senderId = (String) headerAccessor.getSessionAttributes().get("userId");

    if (senderId != null) {
      log.info("Received message from user {}: {}", senderId, message.getContent());

      // Wrap the message into the generic delivery format
      ClientDeliveryMessage deliveryMessage =
          ClientDeliveryMessage.builder()
              .senderId(senderId)
              .content(message.getContent())
              .timestamp(System.currentTimeMillis())
              .build();

      ClientDeliverableData<ClientDeliveryMessage> deliverable =
          ClientDeliverableData.<ClientDeliveryMessage>builder()
              .channelId("/queue/messages") // Default chat channel
              .data(deliveryMessage)
              .receiverUserIds(message.getReceiverIds())
              .build();

      messageRouter.route(deliverable);
    } else {
      log.error("Received message from unauthenticated session!");
    }
  }
}
