package com.commoncoder.scalable_chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A message model specifically for delivery to the end-client. This represents the structured
 * payload the client receives over WebSocket.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class ClientDeliveryMessage {
  private String senderId;
  private String receiverId;
  private String content;
  private long timestamp;
}
