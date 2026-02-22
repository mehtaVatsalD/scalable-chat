package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.model.InterNodeChatMessage;
import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.service.*;
import com.commoncoder.scalable_chat.util.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of MessageRouter that coordinates routing table,
 * liveness, and delivery services.
 */
@Service
public class DefaultMessageRouter implements MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessageRouter.class);

    private final RoutingTable routingTable;
    private final ServerLivenessService livenessService;
    private final RemoteDeliveryService remoteDelivery;
    private final LocalDeliveryService localDelivery;
    private final ServerMetadata serverMetadata;

    private final ExecutorService cleanupExecutor = Executors.newFixedThreadPool(2);

    public DefaultMessageRouter(RoutingTable routingTable,
            ServerLivenessService livenessService,
            RemoteDeliveryService remoteDelivery,
            LocalDeliveryService localDelivery,
            ServerMetadata serverMetadata) {
        this.routingTable = routingTable;
        this.livenessService = livenessService;
        this.remoteDelivery = remoteDelivery;
        this.localDelivery = localDelivery;
        this.serverMetadata = serverMetadata;
    }

    @Override
    public void route(ChatMessage message) {
        log.info("DefaultMessageRouter routing message from {}", message.getSenderId());

        for (String receiverId : message.getReceiverIds()) {
            Map<String, Integer> targetServers = routingTable.getConnectedServers(receiverId);

            if (targetServers.isEmpty()) {
                log.info("User {} offline.", receiverId);
                continue;
            }

            targetServers.keySet().forEach(targetServerId -> {
                if (livenessService.isServerAlive(targetServerId)) {
                    deliverToNode(targetServerId, receiverId, message);
                } else {
                    log.warn("Detected stale routing entry for user {} on dead server {}. Cleaning up.", receiverId,
                            targetServerId);
                    cleanupExecutor.submit(() -> routingTable.removeServerEntry(receiverId, targetServerId));
                }
            });
        }
    }

    private void deliverToNode(String targetServerId, String receiverId, ChatMessage message) {
        if (targetServerId.equals(serverMetadata.getServerId())) {
            // Use MessageMapper for clean DTO conversion
            localDelivery.deliverLocal(receiverId, MessageMapper.toClientDelivery(message, receiverId));
        } else {
            // Use MessageMapper for Inter-Node DTO conversion
            InterNodeChatMessage remoteMsg = MessageMapper.toInterNode(message, receiverId);
            remoteDelivery.deliverRemote(targetServerId, remoteMsg);
        }
    }
}
