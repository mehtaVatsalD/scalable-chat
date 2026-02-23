package com.commoncoder.scalable_chat.controller;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);
    private final ServerMetadata serverMetadata;

    public StatusController(ServerMetadata serverMetadata) {
        this.serverMetadata = serverMetadata;
    }

    @GetMapping("/api/status")
    public ServerMetadata getStatus() {
        log.info("Status check received on node: {}", serverMetadata.getServerId());
        return serverMetadata;
    }
}
