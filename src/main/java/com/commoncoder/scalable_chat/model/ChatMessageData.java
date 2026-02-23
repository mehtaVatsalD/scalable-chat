package com.commoncoder.scalable_chat.model;

import com.commoncoder.scalable_chat.enums.MessageStatus;
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
public class ChatMessageData {
  private Long messageId;
  private Long chatId;
  private String senderId;
  private String content;
  private long timestamp;
  private MessageStatus status;
}
