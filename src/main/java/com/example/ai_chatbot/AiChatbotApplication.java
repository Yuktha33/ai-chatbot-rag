package com.example.ai_chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // memory extraction runs in the background so replies aren't delayed
public class AiChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatbotApplication.class, args);
    }
}
