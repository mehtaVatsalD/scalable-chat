package com.commoncoder.scalable_chat.service;

import java.util.Map;

/**
 * Abstraction for tracking which users are on which servers.
 */
public interface RoutingTable {
    /**
     * Records a connection for a user on this server.
     */
    void registerConnection(String userId, String serverId);

    /**
     * Decrements/Removes a connection for a user.
     */
    void deregisterConnection(String userId, String serverId);

    /**
     * Returns a map of ServerId -> ConnectionCount for a given user.
     */
    Map<String, Integer> getConnectedServers(String userId);

    /**
     * Forcefully removes a server entry for a user (used for dead server cleanup).
     */
    void removeServerEntry(String userId, String serverId);
}
