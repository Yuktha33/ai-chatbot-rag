package com.example.ai_chatbot.repository;

import com.example.ai_chatbot.entity.Conversation;
import com.example.ai_chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByConversationId(String conversationId);
    java.util.List<Conversation> findByUserOrderByIdDesc(User user);
    Optional<Conversation> findByConversationIdAndUser(
            String conversationId,
            User user
    );
}
