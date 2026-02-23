package com.commoncoder.scalable_chat.util;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.model.ClientDeliveryMessage;
import com.commoncoder.scalable_chat.model.InterNodeChatMessage;

/**
 * Utility class for converting between different message models. Centralizes technical
 * transformations between the public API, inter-node protocol, and client delivery formats.
 */
public class MessageMapper {

  /**
   * Converts a raw ChatMessage (from client via API) to an InterNodeChatMessage (for cluster
   * redistribution).
   */
  public static InterNodeChatMessage toInterNode(ChatMessage msg, String receiverId) {
    return InterNodeChatMessage.builder()
        .senderId(msg.getSenderId())
        .receiverId(receiverId)
        .content(msg.getContent())
        .timestamp(msg.getTimestamp())
        .build();
  }

  /**
   * Converts a raw ChatMessage (from client via API) to a ClientDeliveryMessage (for direct socket
   * delivery).
   */
  public static ClientDeliveryMessage toClientDelivery(ChatMessage msg, String receiverId) {
    return ClientDeliveryMessage.builder()
        .senderId(msg.getSenderId())
        .receiverId(receiverId)
        .content(msg.getContent())
        .timestamp(msg.getTimestamp())
        .build();
  }

  /**
   * Converts an InterNodeChatMessage (received from Redis) to a ClientDeliveryMessage (for socket
   * delivery).
   */
  public static ClientDeliveryMessage toClientDelivery(InterNodeChatMessage msg) {
    return ClientDeliveryMessage.builder()
        .senderId(msg.getSenderId())
        .receiverId(msg.getReceiverId())
        .content(msg.getContent())
        .timestamp(msg.getTimestamp())
        .build();
  }
}
