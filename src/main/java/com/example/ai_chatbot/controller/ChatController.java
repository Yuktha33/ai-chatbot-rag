package com.example.ai_chatbot.controller;

import com.example.ai_chatbot.dto.ChatRequest;
import com.example.ai_chatbot.dto.ChatResponse;
import com.example.ai_chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        return chatService.chat(auth.getName(), request.getConversationId(), request.getMessage(), request.wantsDocuments());
    }

    @GetMapping("/conversations")
    public List<Map<String, String>> conversations(Authentication auth) {
        return chatService.listConversations(auth.getName());
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, String>> messages(@PathVariable String id, Authentication auth) {
        return chatService.getMessages(auth.getName(), id);
    }
}
