package com.example.ai_chatbot.repository;

import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUser(User user);

    Optional<UserMemory> findByUserAndMemoryKey(User user, String memoryKey);

    Optional<UserMemory> findByIdAndUser(Long id, User user);

    long countByUser(User user);
}
