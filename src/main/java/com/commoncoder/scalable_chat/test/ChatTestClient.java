package com.commoncoder.scalable_chat.test;

import com.commoncoder.scalable_chat.model.ChatMessage;
import com.commoncoder.scalable_chat.model.ClientDeliveryMessage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Interactive test client for Scenario 1 (Local Delivery). Run two instances of this to test
 * cross-user messaging on the same server.
 */
public class ChatTestClient {

  private static final Logger log = LoggerFactory.getLogger(ChatTestClient.class);

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    if (args.length < 1) {
      System.out.println("Usage: ChatTestClient <userId> [serverUrl]");
      return;
    }

    String userId = args[0];
    String url = args.length > 1 ? args[1] : "http://localhost:8080/chat-ws";

    List<Transport> transports = new ArrayList<>();
    transports.add(new WebSocketTransport(new StandardWebSocketClient()));
    SockJsClient sockJsClient = new SockJsClient(transports);

    WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("userId", userId);

    log.info("Connecting to {} as user: {}...", url, userId);

    StompSession session =
        stompClient
            .connectAsync(
                url,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                  @Override
                  public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    log.info("CONNECTED! Session ID: {}", session.getSessionId());

                    // Subscribe to private message queue
                    session.subscribe(
                        "/user/queue/messages",
                        new StompFrameHandler() {
                          @Override
                          public Type getPayloadType(StompHeaders headers) {
                            return ClientDeliveryMessage.class;
                          }

                          @Override
                          public void handleFrame(StompHeaders headers, Object payload) {
                            ClientDeliveryMessage msg = (ClientDeliveryMessage) payload;
                            log.info(
                                ">>> [FROM {} TO {}]: {}",
                                msg.getSenderId(),
                                msg.getReceiverId(),
                                msg.getContent());
                          }
                        });
                  }

                  @Override
                  public void handleException(
                      StompSession session,
                      StompCommand command,
                      StompHeaders headers,
                      byte[] payload,
                      Throwable exception) {
                    log.error("STOMP Session Error: {}", exception.getMessage(), exception);
                  }
                })
            .get();

    Scanner scanner = new Scanner(System.in);
    System.out.println("Format: <targetUserId> <message>");
    System.out.println("Example: user2 Hello there!");
    System.out.println("Type 'quit' to exit.");

    while (true) {
      String line = scanner.nextLine();
      if ("quit".equalsIgnoreCase(line)) break;

      String[] parts = line.split(" ", 2);
      if (parts.length < 2) {
        System.out.println("Invalid format. Use: <targetUserId> <message>");
        continue;
      }

      String target = parts[0];
      String text = parts[1];

      ChatMessage msg =
          ChatMessage.builder()
              .senderId(userId)
              .receiverIds(Collections.singletonList(target))
              .content(text)
              .timestamp(System.currentTimeMillis())
              .build();

      session.send("/app/message", msg);
      log.info("Sent to {}: {}", target, text);
    }

    session.disconnect();
    log.info("Disconnected.");
  }
}
