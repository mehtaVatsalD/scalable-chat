package com.commoncoder.scalable_chat.listener;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.service.RoutingTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final RoutingTable routingTable;
    private final ServerMetadata serverMetadata;

    public WebSocketEventListener(RoutingTable routingTable, ServerMetadata serverMetadata) {
        this.routingTable = routingTable;
        this.serverMetadata = serverMetadata;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        String serverId = serverMetadata.getServerId();

        if (userId != null) {
            routingTable.registerConnection(userId, serverId);
            log.info("User {} connected to server {}.", userId, serverId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        String serverId = serverMetadata.getServerId();

        if (userId != null) {
            routingTable.deregisterConnection(userId, serverId);
            log.info("User {} disconnected from server {}.", userId, serverId);
        }
    }
}
