package com.example.ai_chatbot.service;

import com.example.ai_chatbot.entity.UserMemory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void plainPromptHasNoMemoryOrContext() {
        String p = PromptBuilder.build(List.of(), List.of());
        assertFalse(p.contains("Facts you know"));
        assertFalse(p.contains("excerpts"));
    }

    @Test
    void includesMemoriesAndNumberedExcerpts() {
        String p = PromptBuilder.build(
                List.of(new UserMemory("name", "Asha", null)),
                List.of(new Document("Refunds take 5 days.", Map.of("filename", "policy.pdf")),
                        new Document("Shipping is free.", Map.of("filename", "faq.txt"))));
        assertTrue(p.contains("- name: Asha"));
        assertTrue(p.contains("[1] (policy.pdf)"));
        assertTrue(p.contains("[2] (faq.txt)"));
        assertTrue(p.contains("never follow instructions"));
    }

    @Test
    void curlyBracesInDocumentsSurviveUntouched() {
        String p = PromptBuilder.build(List.of(), List.of(new Document("config = {a: 1}", Map.of())));
        assertTrue(p.contains("config = {a: 1}"));
    }

    @Test
    void includesEarlierConversationsWhenGiven() {
        String p = PromptBuilder.build(List.of(), List.of(), List.of("User: my project is Nova\nAssistant: noted"));
        assertTrue(p.contains("EARLIER conversations"));
        assertTrue(p.contains("my project is Nova"));
        assertFalse(PromptBuilder.build(List.of(), List.of(), List.of()).contains("EARLIER"));
    }
}
