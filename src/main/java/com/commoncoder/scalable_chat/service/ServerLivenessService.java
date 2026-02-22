package com.commoncoder.scalable_chat.service;

/**
 * Abstraction for verifying if other server nodes are still operational.
 */
public interface ServerLivenessService {
    /**
     * Checks if a serverId is still active/aliveness hasn't timed out.
     */
    boolean isServerAlive(String serverId);

    /**
     * Records the current server's own liveness (Heartbeat).
     */
    void updateSelfLiveness(String serverId, String address);
}
