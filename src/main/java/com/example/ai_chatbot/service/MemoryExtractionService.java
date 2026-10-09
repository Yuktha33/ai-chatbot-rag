package com.example.ai_chatbot.service;

import com.example.ai_chatbot.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Long-term memory writer: after each reply, asks the LLM whether the user stated any durable
 * fact about themselves (name, skills, projects, goals, preferences...) and stores it.
 * Runs in the background (@Async); failures are logged only.
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    private static final String INSTRUCTIONS = """
        You extract long-term memory about a user from ONE message they wrote.

        Extract durable facts the user states about THEMSELVES: name, job or studies, skills and tools,
        projects they are building, goals, location, and stable preferences.
        Prefer these keys when they fit: name, occupation, education, skills, current_project, goal,
        location, favorite_language, preferences. Otherwise invent a short snake_case key.

        Examples:

        User: My name is Yuktha.
        Output: [{"key":"name","value":"Yuktha"}]

        User: I work with Java and Spring Boot.
        Output: [{"key":"skills","value":"Java and Spring Boot"}]

        User: I'm building an AI chatbot with Spring Boot and Ollama.
        Output: [{"key":"current_project","value":"AI chatbot with Spring Boot and Ollama"}]

        User: I'm preparing for backend developer interviews.
        Output: [{"key":"goal","value":"preparing for backend developer interviews"}]

        User: What is the capital of France?
        Output: []

        Rules:
        - Output ONLY a JSON array, nothing else. No markdown, no explanations.
        - Every element is an object with exactly "key" and "value", both strings.
        - Only facts the user explicitly stated about themselves. Never guess.
        - Questions, requests and general knowledge are NOT facts: output [].
        """;

    private final ChatClient chatClient;
    private final UserMemoryService memoryService;
    private final boolean enabled;

    public MemoryExtractionService(ChatClient.Builder builder,
                                   UserMemoryService memoryService,
                                   @Value("${app.memory.enabled:true}") boolean enabled) {
        this.chatClient = builder.build();
        this.memoryService = memoryService;
        this.enabled = enabled;
    }

    @Async
    public void extractAndStore(User user, String userMessage) {
        if (!enabled || userMessage == null || userMessage.length() < 6) {
            return;
        }
        try {
            // Message objects (not .system()/.user() strings) so braces in text aren't treated as template vars
            String raw = chatClient.prompt()
                    .messages(new SystemMessage(INSTRUCTIONS), new UserMessage(userMessage))
                    .call()
                    .content();

            List<MemoryFactParser.Fact> facts = MemoryFactParser.parse(raw);
            log.debug("Memory extraction raw output: {}", raw);

            for (MemoryFactParser.Fact fact : facts) {
                String key = fact.key().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                String value = fact.value().trim();
                if (key.isBlank() || value.isBlank()) {
                    continue;
                }
                memoryService.saveMemory(user, truncate(key, 60), truncate(value, 300));
            }
        } catch (Exception e) {
            log.warn("Memory extraction skipped: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
