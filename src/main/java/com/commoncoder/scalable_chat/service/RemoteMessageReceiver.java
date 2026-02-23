package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.InterNodeChatMessage;

/** Abstraction for a component that receives messages from the inter-node transport. */
public interface RemoteMessageReceiver {
  /** Called when a message arrives from another server node. */
  void onMessage(InterNodeChatMessage message);
}
