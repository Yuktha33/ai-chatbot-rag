package com.example.ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "Conversation ID cannot be empty")
    private String conversationId;

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 5000, message = "Message cannot exceed 5000 characters")
    private String message;

    /** Search the user's uploaded documents (RAG). Defaults to true when omitted. */
    private Boolean useDocuments;

    public ChatRequest() {
    }

    public ChatRequest(String conversationId, String message) {
        this.conversationId = conversationId;
        this.message = message;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getUseDocuments() { return useDocuments; }
    public void setUseDocuments(Boolean useDocuments) { this.useDocuments = useDocuments; }

    public boolean wantsDocuments() { return useDocuments == null || useDocuments; }
}
