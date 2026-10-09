package com.example.ai_chatbot.repository;

import com.example.ai_chatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByIdAsc(
            String conversationId
    );
}