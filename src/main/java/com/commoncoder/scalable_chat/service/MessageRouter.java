package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.ChatMessage;

/**
 * High-level orchestrator for processing and routing chat messages.
 */
public interface MessageRouter {
    /**
     * Routes an incoming message from a client to its target receivers.
     */
    void route(ChatMessage message);
}
