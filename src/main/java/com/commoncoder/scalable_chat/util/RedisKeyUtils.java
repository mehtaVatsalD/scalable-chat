package com.commoncoder.scalable_chat.util;

public class RedisKeyUtils {

    private static final String BASE_PREFIX = "chat:";

    // Prefix for server heartbeat/registration keys (Value: topicName)
    private static final String SERVER_INFO_PREFIX = BASE_PREFIX + "server:info:";

    // Prefix for user routing tables (Hash: serverId -> connectionCount)
    private static final String USER_ROUTING_PREFIX = BASE_PREFIX + "user:routing:";

    // Prefix for server-specific Pub/Sub topics
    private static final String SERVER_TOPIC_PREFIX = BASE_PREFIX + "topic:";

    /**
     * Builds the key for a server's registration/heartbeat.
     */
    public static String getServerInfoKey(String serverId) {
        return SERVER_INFO_PREFIX + serverId;
    }

    /**
     * Builds the key for a user's routing table (Hash).
     */
    public static String getUserRoutingKey(String userId) {
        return USER_ROUTING_PREFIX + userId;
    }

    /**
     * Builds the name of the Redis Pub/Sub topic for a specific server.
     */
    public static String getServerTopicName(String serverId) {
        return SERVER_TOPIC_PREFIX + serverId;
    }
}
