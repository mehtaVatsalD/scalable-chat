package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.service.LocalDeliveryService;
import com.commoncoder.scalable_chat.service.MessageRouter;
import com.commoncoder.scalable_chat.service.RemoteDeliveryService;
import com.commoncoder.scalable_chat.service.RoutingTable;
import com.commoncoder.scalable_chat.service.ServerLivenessService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of MessageRouter that coordinates routing table, liveness, and delivery services.
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

  public DefaultMessageRouter(
      RoutingTable routingTable,
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
  public void route(ClientDeliverableData<?> deliverable) {
    log.info(
        "DefaultMessageRouter routing message for {} receivers",
        deliverable.getReceiverUserIds().size());

    // Group receivers by their connected servers
    Map<String, List<String>> serverToReceivers = new HashMap<>();

    for (String receiverId : deliverable.getReceiverUserIds()) {
      Map<String, Integer> targetServers = routingTable.getConnectedServers(receiverId);

      if (targetServers.isEmpty()) {
        log.info("User {} offline.", receiverId);
        continue;
      }

      targetServers
          .keySet()
          .forEach(
              targetServerId -> {
                if (livenessService.isServerAlive(targetServerId)) {
                  serverToReceivers
                      .computeIfAbsent(targetServerId, k -> new ArrayList<>())
                      .add(receiverId);
                } else {
                  log.warn(
                      "Detected stale routing entry for user {} on dead server {}. Cleaning up.",
                      receiverId,
                      targetServerId);
                  cleanupExecutor.submit(
                      () -> routingTable.removeServerEntry(receiverId, targetServerId));
                }
              });
    }

    // Deliver to each server
    serverToReceivers.forEach(
        (serverId, receivers) -> {
          // Create a specific deliverable for this server's users
          ClientDeliverableData<?> nodeDeliverable =
              deliverable.toBuilder().receiverUserIds(receivers).build();

          if (serverId.equals(serverMetadata.getServerId())) {
            localDelivery.deliverLocal(nodeDeliverable);
          } else {
            remoteDelivery.deliverRemote(serverId, nodeDeliverable);
          }
        });
  }
}
