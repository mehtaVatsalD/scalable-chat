package com.commoncoder.scalable_chat.model;

import java.util.List;

public class ChatMessage {
    private String senderId;
    private List<String> receiverIds;
    private String content;
    private long timestamp;

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String senderId, List<String> receiverIds, String content) {
        this();
        this.senderId = senderId;
        this.receiverIds = receiverIds;
        this.content = content;
    }

    // Getters and Setters
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public List<String> getReceiverIds() {
        return receiverIds;
    }

    public void setReceiverIds(List<String> receiverIds) {
        this.receiverIds = receiverIds;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "senderId='" + senderId + '\'' +
                ", receiverIds=" + receiverIds +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
