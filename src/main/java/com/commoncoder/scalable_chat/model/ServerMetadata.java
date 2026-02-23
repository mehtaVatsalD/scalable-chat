package com.commoncoder.scalable_chat.model;

import com.commoncoder.scalable_chat.util.RedisKeyUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class ServerMetadata {
  private String serverId;
  private int nodeId;

  public String getTopicName() {
    return RedisKeyUtils.getServerTopicName(serverId);
  }
}
