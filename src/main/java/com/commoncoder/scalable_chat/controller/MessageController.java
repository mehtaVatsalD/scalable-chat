package com.commoncoder.scalable_chat.controller;

import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.service.MessageRouter;
import java.util.List;
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

  @MessageMapping("/message/new")
  public void handleSendNewChatMessage(
      @Payload SendNewChatMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
    String senderId = (String) headerAccessor.getSessionAttributes().get("userId");

    if (senderId != null) {
      log.info("Received message from user {}: {}", senderId, request.getContent());

      // Wrap the message into the generic delivery format
      ChatMessageData deliveryMessage =
          ChatMessageData.builder()
              .senderId(senderId)
              .content(request.getContent())
              .timestamp(System.currentTimeMillis())
              .build();

      // 1. Deliver to sender's own connections first
      ClientDeliverableData<ChatMessageData> senderDeliverable =
          ClientDeliverableData.<ChatMessageData>builder()
              .channelId("/queue/messages")
              .data(deliveryMessage)
              .receiverUserIds(List.of(senderId))
              .build();
      messageRouter.route(senderDeliverable);

      // 2. Deliver to receivers
      ClientDeliverableData<ChatMessageData> receiverDeliverable =
          ClientDeliverableData.<ChatMessageData>builder()
              .channelId("/queue/messages") // Default chat channel
              .data(deliveryMessage)
              .receiverUserIds(request.getReceiverIds())
              .build();

      messageRouter.route(receiverDeliverable);
    } else {
      log.error("Received message from unauthenticated session!");
    }
  }
}
