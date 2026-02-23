package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.service.LocalDeliveryService;
import com.commoncoder.scalable_chat.service.RemoteMessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Concrete implementation of RemoteMessageReceiver that bridges inter-node messages to the locally
 * connected clients.
 */
@Service
public class DefaultRemoteMessageReceiver implements RemoteMessageReceiver {

  private static final Logger log = LoggerFactory.getLogger(DefaultRemoteMessageReceiver.class);
  private final LocalDeliveryService localDelivery;

  public DefaultRemoteMessageReceiver(LocalDeliveryService localDelivery) {
    this.localDelivery = localDelivery;
  }

  @Override
  public void onMessage(ClientDeliverableData<?> deliverable) {
    log.info("Received inter-node message for users {}", deliverable.getReceiverUserIds());

    // Deliver to each local users
    localDelivery.deliverLocal(deliverable);
  }
}
