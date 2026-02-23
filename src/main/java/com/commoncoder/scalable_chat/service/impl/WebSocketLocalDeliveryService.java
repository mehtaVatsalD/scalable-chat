package com.commoncoder.scalable_chat.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.commoncoder.scalable_chat.model.ClientDeliveryMessage;
import com.commoncoder.scalable_chat.service.LocalDeliveryService;

@Service
public class WebSocketLocalDeliveryService implements LocalDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(WebSocketLocalDeliveryService.class);

  private final SimpMessagingTemplate messagingTemplate;

  public WebSocketLocalDeliveryService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void deliverLocal(String userId, ClientDeliveryMessage message) {
    log.info("Delivering via WebSocket to user {}: {}", userId, message.getContent());
    messagingTemplate.convertAndSendToUser(userId, "/queue/messages", message);
  }
}
