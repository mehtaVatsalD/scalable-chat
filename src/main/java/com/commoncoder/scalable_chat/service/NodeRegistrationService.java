package com.commoncoder.scalable_chat.service;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * Service responsible for registering the node with the inter-node communication system on startup
 * and cleanup on shutdown.
 */
@Service
public class NodeRegistrationService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(NodeRegistrationService.class);

  private final ServerMetadata serverMetadata;
  private final RemoteSubscriptionService subscriptionService;
  private final RemoteMessageReceiver remoteMessageReceiver;

  public NodeRegistrationService(
      ServerMetadata serverMetadata,
      RemoteSubscriptionService subscriptionService,
      RemoteMessageReceiver remoteMessageReceiver) {
    this.serverMetadata = serverMetadata;
    this.subscriptionService = subscriptionService;
    this.remoteMessageReceiver = remoteMessageReceiver;
  }

  @Override
  public void run(ApplicationArguments args) {
    String topic = serverMetadata.getTopicName();
    log.info("Node initializing: Subscribing to inter-node channel {}", topic);

    // Subscribe to our unique topic to receive messages from other nodes
    subscriptionService.subscribe(topic, remoteMessageReceiver);
  }

  @PreDestroy
  public void cleanup() {
    String topic = serverMetadata.getTopicName();
    log.info("Node shutting down: Unsubscribing from inter-node channel {}", topic);
    subscriptionService.unsubscribe(topic);
  }
}
