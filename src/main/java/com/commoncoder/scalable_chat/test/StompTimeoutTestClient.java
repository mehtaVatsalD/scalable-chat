package com.commoncoder.scalable_chat.test;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A STOMP client designed to FAIL heartbeats.
 * It bypasses the TaskScheduler requirement by providing a No-Op
 * implementation.
 */
public class StompTimeoutTestClient {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        if (args.length < 1) {
            System.out.println("Usage: StompTimeoutTestClient <userId>");
            return;
        }

        String userId = args[0];
        String url = "ws://localhost:8080/chat-ws";

        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new StringMessageConverter());

        // This is THE KEY: We provide a TaskScheduler that does NOTHING.
        // This satisfies Spring's validation but prevents any heartbeats from being
        // sent.
        stompClient.setTaskScheduler(new NoOpTaskScheduler());

        System.out.println("Connecting to " + url + " as user: " + userId + " (SILENT MODE - BYPASSING HEARTBEATS)...");

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userId", userId);

        // Promise heartbeats to the server (15s out, 15s in)
        connectHeaders.setHeartbeat(new long[] { 15000, 15000 });

        StompSession session = stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        System.out.println("CONNECTED! Session ID: " + session.getSessionId());
                        System.out.println("Now staying silent. Server should kick us out in ~30-45 seconds.");
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        System.out.println("TRANSPORT ERROR/DISCONNECT: " + exception.getMessage());
                    }
                }).get(5, TimeUnit.SECONDS);

        // Keep main thread alive
        while (session.isConnected()) {
            Thread.sleep(1000);
        }

        System.out.println("Client detected disconnection. Exiting.");
    }

    /**
     * A No-Op TaskScheduler to bypass Spring's validation while staying silent.
     */
    private static class NoOpTaskScheduler implements TaskScheduler {
        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            return null;
        }
    }
}
