package com.commoncoder.scalable_chat.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class CreateNewGroupChatRequest {
    private String creatorUserId;
    private List<String> participantUserIds;
    private String chatName;
}
