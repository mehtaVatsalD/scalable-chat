package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;

public interface MessageService {
  void processNewMessage(String senderId, SendNewChatMessageRequest request);
}
