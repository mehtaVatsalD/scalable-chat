package com.commoncoder.scalable_chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScalableChatApplication {

  public static void main(String[] args) {
    SpringApplication.run(ScalableChatApplication.class, args);
  }
}
