package com.example.ai_chatbot.dto;

import com.example.ai_chatbot.entity.KnowledgeDocument;

import java.time.Instant;

public record DocumentInfo(Long id, String filename, int chunks, Instant createdAt) {

    public static DocumentInfo from(KnowledgeDocument d) {
        return new DocumentInfo(d.getId(), d.getFilename(), d.getChunkCount(), d.getCreatedAt());
    }
}
