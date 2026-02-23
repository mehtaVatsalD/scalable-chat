package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.InterNodeChatMessage;

/** Abstraction for sending messages to remote server nodes. */
public interface RemoteDeliveryService {
  /** Sends a message to a specific remote server's transport/topic. */
  void deliverRemote(String targetServerId, InterNodeChatMessage message);
}
