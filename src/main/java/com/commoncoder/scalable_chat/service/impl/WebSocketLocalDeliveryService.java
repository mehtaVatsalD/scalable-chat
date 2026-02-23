package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.service.LocalDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketLocalDeliveryService implements LocalDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(WebSocketLocalDeliveryService.class);

  private final SimpMessagingTemplate messagingTemplate;

  public WebSocketLocalDeliveryService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public <T> void deliverLocal(ClientDeliverableData<T> deliverable) {
    deliverable
        .getReceiverUserIds()
        .forEach(
            userId -> {
              log.info("Delivering via WebSocket to user {}: {}", userId, deliverable.getData());
              messagingTemplate.convertAndSendToUser(
                  userId, deliverable.getChannelId(), deliverable.getData());
            });
  }
}
