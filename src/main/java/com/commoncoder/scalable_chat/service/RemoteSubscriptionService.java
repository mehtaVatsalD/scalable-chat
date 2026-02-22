package com.commoncoder.scalable_chat.service;

/**
 * Abstraction for subscribing to the inter-node communication system.
 */
public interface RemoteSubscriptionService {
    /**
     * Start listening for messages on a specific channel/topic.
     */
    void subscribe(String channelId, RemoteMessageReceiver receiver);

    /**
     * Stop listening on a specific channel/topic.
     */
    void unsubscribe(String channelId);
}
