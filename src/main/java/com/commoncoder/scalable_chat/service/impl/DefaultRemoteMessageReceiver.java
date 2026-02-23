package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.InterNodeChatMessage;
import com.commoncoder.scalable_chat.service.LocalDeliveryService;
import com.commoncoder.scalable_chat.service.RemoteMessageReceiver;
import com.commoncoder.scalable_chat.util.MessageMapper;
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
  public void onMessage(InterNodeChatMessage message) {
    log.info(
        "Received inter-node message for user {}: {}",
        message.getReceiverId(),
        message.getContent());

    // Use MessageMapper to bridge inter-node message to local client format
    localDelivery.deliverLocal(message.getReceiverId(), MessageMapper.toClientDelivery(message));
  }
}
