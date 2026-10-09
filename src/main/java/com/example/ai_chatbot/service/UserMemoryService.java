package com.example.ai_chatbot.service;

import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.entity.UserMemory;
import com.example.ai_chatbot.repository.UserMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserMemoryService {

    private final UserMemoryRepository repository;
    private final int maxPerUser;

    public UserMemoryService(UserMemoryRepository repository,
                             @Value("${app.memory.max-per-user:50}") int maxPerUser) {
        this.repository = repository;
        this.maxPerUser = maxPerUser;
    }

    public List<UserMemory> getMemories(User user) {
        return repository.findByUser(user);
    }

    /** Insert or update. Returns false if the per-user cap stops a new entry. */
    public boolean saveMemory(User user, String key, String value) {
        UserMemory memory = repository.findByUserAndMemoryKey(user, key).orElse(null);
        if (memory == null) {
            if (repository.countByUser(user) >= maxPerUser) {
                return false;
            }
            memory = new UserMemory(key, value, user);
        } else {
            memory.setMemoryValue(value);
        }
        repository.save(memory);
        return true;
    }

    public void delete(User user, Long id) {
        UserMemory memory = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
        repository.delete(memory);
    }
}
