package com.commoncoder.scalable_chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A message model specifically for inter-node communication. This represents a message targeted at
 * a SPECIFIC receiver on a remote node.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class InterNodeChatMessage {
  private String senderId;
  private String receiverId;
  private String content;
  private long timestamp;
}
