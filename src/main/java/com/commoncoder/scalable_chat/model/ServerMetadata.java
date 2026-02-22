package com.commoncoder.scalable_chat.model;

import com.commoncoder.scalable_chat.util.RedisKeyUtils;

public class ServerMetadata {
    private String serverId;

    public ServerMetadata() {
    }

    public ServerMetadata(String serverId) {
        this.serverId = serverId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getTopicName() {
        return RedisKeyUtils.getServerTopicName(serverId);
    }
}
