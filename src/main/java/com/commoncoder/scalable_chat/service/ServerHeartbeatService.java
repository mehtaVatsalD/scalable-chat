package com.commoncoder.scalable_chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.commoncoder.scalable_chat.model.ServerMetadata;

@Service
public class ServerHeartbeatService {

  private static final Logger log = LoggerFactory.getLogger(ServerHeartbeatService.class);

  private final ServerMetadata serverMetadata;
  private final ServerLivenessService livenessService;

  public ServerHeartbeatService(
      ServerMetadata serverMetadata, ServerLivenessService livenessService) {
    this.serverMetadata = serverMetadata;
    this.livenessService = livenessService;
  }

  @Scheduled(fixedRate = 15000)
  public void sendHeartbeat() {
    String serverId = serverMetadata.getServerId();
    String topicName = serverMetadata.getTopicName();

    livenessService.updateSelfLiveness(serverId, topicName);
    log.info("Registered liveness for server: {}", serverId);
  }
}
