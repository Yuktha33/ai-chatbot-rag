package com.example.ai_chatbot.service;
import com.example.ai_chatbot.entity.ChatMessage;
import com.example.ai_chatbot.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatMessage saveMessage(
            String conversationId,
            String message,
            String role) {

        ChatMessage chatMessage =
                new ChatMessage(conversationId, message, role);

        return chatMessageRepository.save(chatMessage);
    }

    public List<ChatMessage> getConversation(
            String conversationId) {

        return chatMessageRepository
                .findByConversationIdOrderByIdAsc(conversationId);
    }
}
