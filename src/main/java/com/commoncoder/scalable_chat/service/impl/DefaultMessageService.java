package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.entity.ChatParticipant;
import com.commoncoder.scalable_chat.entity.Message;
import com.commoncoder.scalable_chat.model.ChatMessageData;
import com.commoncoder.scalable_chat.model.ClientDeliverableData;
import com.commoncoder.scalable_chat.model.SendNewChatMessageRequest;
import com.commoncoder.scalable_chat.repository.ChatParticipantRepository;
import com.commoncoder.scalable_chat.repository.MessageRepository;
import com.commoncoder.scalable_chat.service.IdGeneratorService;
import com.commoncoder.scalable_chat.service.MessageRouter;
import com.commoncoder.scalable_chat.service.MessageService;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultMessageService implements MessageService {

  private static final Logger log = LoggerFactory.getLogger(DefaultMessageService.class);
  private final MessageRouter messageRouter;
  private final IdGeneratorService idGeneratorService;
  private final MessageRepository messageRepository;
  private final ChatParticipantRepository chatParticipantRepository;

  public DefaultMessageService(
      MessageRouter messageRouter,
      IdGeneratorService idGeneratorService,
      MessageRepository messageRepository,
      ChatParticipantRepository chatParticipantRepository) {
    this.messageRouter = messageRouter;
    this.idGeneratorService = idGeneratorService;
    this.messageRepository = messageRepository;
    this.chatParticipantRepository = chatParticipantRepository;
  }

  @Override
  public void processNewMessage(String senderId, SendNewChatMessageRequest request) {
    log.info("Processing message for chat {} from user {}", request.getChatId(), senderId);

    long messageId = idGeneratorService.nextId();
    long timestamp = System.currentTimeMillis();

    // Build the message payload
    ChatMessageData deliveryMessage =
        ChatMessageData.builder()
            .messageId(messageId)
            .chatId(request.getChatId())
            .senderId(senderId)
            .content(request.getContent())
            .timestamp(timestamp)
            .build();

    // 1. Send to sender itself
    ClientDeliverableData<ChatMessageData> senderDeliverable =
        ClientDeliverableData.<ChatMessageData>builder()
            .channelId("/queue/messages")
            .data(deliveryMessage)
            .receiverUserIds(List.of(senderId))
            .build();
    messageRouter.route(senderDeliverable);

    // 2. Persist the message
    Message messageEntity =
        Message.builder()
            .id(messageId)
            .chatId(request.getChatId())
            .senderId(senderId)
            .content(request.getContent())
            .timestamp(timestamp)
            .build();
    messageRepository.save(messageEntity);

    // 3. Find all participants by chatid
    List<ChatParticipant> participants =
        chatParticipantRepository.findAllByChatId(request.getChatId());
    List<String> participantUserIds =
        participants.stream().map(ChatParticipant::getUserId).collect(Collectors.toList());

    // 4. Send to all participants
    ClientDeliverableData<ChatMessageData> broadcastDeliverable =
        ClientDeliverableData.<ChatMessageData>builder()
            .channelId("/queue/messages")
            .data(deliveryMessage)
            .receiverUserIds(participantUserIds)
            .build();

    messageRouter.route(broadcastDeliverable);
  }
}
