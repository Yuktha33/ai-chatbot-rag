package com.example.ai_chatbot.dto;

import java.util.List;

public record ChatResponse(String response, List<SourceRef> sources) {


    public record SourceRef(int index, String filename, String snippet) {
    }
}
