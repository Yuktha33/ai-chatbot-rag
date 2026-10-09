package com.example.ai_chatbot.controller;

import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.repository.UserRepository;
import com.example.ai_chatbot.service.UserMemoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Lets users see and delete what the bot has remembered about them. */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    public record MemoryDto(Long id, String key, String value) {
    }

    private final UserMemoryService memoryService;
    private final UserRepository userRepository;

    public MemoryController(UserMemoryService memoryService, UserRepository userRepository) {
        this.memoryService = memoryService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<MemoryDto> list(Authentication auth) {
        return memoryService.getMemories(user(auth)).stream()
                .map(m -> new MemoryDto(m.getId(), m.getMemoryKey(), m.getMemoryValue()))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        memoryService.delete(user(auth), id);
    }

    private User user(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
