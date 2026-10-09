package com.example.ai_chatbot.service;

import com.example.ai_chatbot.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-conversation recall. Every finished exchange (user message + assistant reply) is embedded
 * into the vector store; when the user writes in ANY chat, the most similar exchanges from their OTHER
 * chats are retrieved and shown to the model.
 *
 * Isolation: these vectors use the metadata key "chatUserId" (not "userId"), so the document
 * search in RagService - which filters on "userId" - can never return chat history as a document.
 */
@Service
public class ChatRecallService {

    private static final Logger log = LoggerFactory.getLogger(ChatRecallService.class);
    private static final int CLIP = 700;

    private final VectorStore vectorStore;
    private final boolean enabled;
    private final int topK;
    private final double threshold;

    public ChatRecallService(VectorStore vectorStore,
                             @Value("${app.recall.enabled:true}") boolean enabled,
                             @Value("${app.recall.top-k:3}") int topK,
                             @Value("${app.recall.similarity-threshold:0.5}") double threshold) {
        this.vectorStore = vectorStore;
        this.enabled = enabled;
        this.topK = topK;
        this.threshold = threshold;
    }

    /** Index one exchange in the background. */
    @Async
    public void remember(User user, String conversationId, String userMessage, String reply) {
        if (!enabled || userMessage == null || userMessage.length() < 15 || reply == null) {
            return;
        }
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("chatUserId", String.valueOf(user.getId()));
            meta.put("conversationId", conversationId);
            String text = "User: " + clip(userMessage) + "\nAssistant: " + clip(reply);
            vectorStore.add(List.of(new Document(text, meta)));
        } catch (RuntimeException e) {
            log.warn("Could not index chat exchange: {}", e.getMessage());
        }
    }

    /** Exchanges from the user's other conversations that are relevant to the new message. */
    public List<Document> recall(User user, String currentConversationId, String query) {
        if (!enabled || query == null || query.length() < 8) {
            return List.of();
        }
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(b.and(
                            b.eq("chatUserId", String.valueOf(user.getId())),
                            b.ne("conversationId", currentConversationId)).build())
                    .build());
            return hits == null ? List.of() : hits;
        } catch (RuntimeException e) {
            log.warn("Chat recall failed, continuing without it: {}", e.getMessage());
            return List.of();
        }
    }

    private static String clip(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= CLIP ? t : t.substring(0, CLIP) + "…";
    }
}
