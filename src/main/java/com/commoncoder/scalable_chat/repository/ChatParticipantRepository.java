package com.commoncoder.scalable_chat.repository;

import com.commoncoder.scalable_chat.entity.ChatParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    List<ChatParticipant> findAllByChatId(Long chatId);
}
