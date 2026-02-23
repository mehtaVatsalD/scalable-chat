package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.ClientDeliverableData;

/** Abstraction for delivering messages to clients connected to the current node. */
public interface LocalDeliveryService {
  /** Delivers a structured message to a locally connected user. */
  <T> void deliverLocal(ClientDeliverableData<T> deliverable);
}
