package com.example.ai_chatbot.repository;

import com.example.ai_chatbot.entity.KnowledgeDocument;
import com.example.ai_chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByUserOrderByIdDesc(User user);

    Optional<KnowledgeDocument> findByIdAndUser(Long id, User user);

    long countByUser(User user);
}
