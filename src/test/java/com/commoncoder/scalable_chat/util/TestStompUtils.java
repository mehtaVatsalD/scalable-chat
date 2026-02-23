package com.commoncoder.scalable_chat.util;

import com.commoncoder.scalable_chat.model.ChatMessageData;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

public class TestStompUtils {
  private static final Logger log = LoggerFactory.getLogger(TestStompUtils.class);

  public static StompSession connectStomp(
      String url, String userId, BlockingQueue<ChatMessageData> messageQueue) throws Exception {
    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("userId", userId);

    StompSessionHandler sessionHandler =
        new StompSessionHandlerAdapter() {
          @Override
          public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            if (messageQueue != null) {
              session.subscribe(
                  "/user/queue/messages",
                  new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                      return ChatMessageData.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                      log.info("User {} received message: {}", userId, payload);
                      messageQueue.add((ChatMessageData) payload);
                    }
                  });
            }
          }
        };

    return stompClient
        .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, sessionHandler)
        .get(10, TimeUnit.SECONDS);
  }
}
