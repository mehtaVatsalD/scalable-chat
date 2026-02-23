package com.commoncoder.scalable_chat.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import org.springframework.messaging.converter.StringMessageConverter;
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
 * A simple STOMP client to test the scalable chat backend. Run with: mvn exec:java
 * -Dexec.mainClass="com.commoncoder.scalable_chat.test.StompTestClient" -Dexec.args="user123"
 */
public class StompTestClient {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    if (args.length < 1) {
      System.out.println("Usage: StompTestClient <userId>");
      return;
    }

    String userId = args[0];
    String url = "ws://localhost:8080/chat-ws";

    List<Transport> transports = new ArrayList<>();
    transports.add(new WebSocketTransport(new StandardWebSocketClient()));
    SockJsClient sockJsClient = new SockJsClient(transports);

    WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
    stompClient.setMessageConverter(new StringMessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("userId", userId);

    System.out.println("Connecting to " + url + " as user: " + userId + "...");

    StompSession session =
        stompClient
            .connectAsync(
                url,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                  @Override
                  public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    System.out.println("CONNECTED! Session ID: " + session.getSessionId());
                  }

                  @Override
                  public void handleTransportError(StompSession session, Throwable exception) {
                    System.err.println("Transport Error: " + exception.getMessage());
                    exception.printStackTrace();
                  }
                })
            .get();

    System.out.println("Connection maintained. Press ENTER to disconnect.");
    new Scanner(System.in).nextLine();

    session.disconnect();
    System.out.println("Disconnected.");
  }
}
