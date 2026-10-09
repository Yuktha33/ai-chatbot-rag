package com.example.ai_chatbot.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Metadata about an uploaded document. The chunks/embeddings live in the vector store. */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Random key stored in every chunk's metadata ("docId"), used to delete the chunks later. */
    @Column(nullable = false, unique = true)
    private String docKey;

    @Column(nullable = false)
    private String filename;

    private int chunkCount;

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public KnowledgeDocument() {
    }

    public KnowledgeDocument(String docKey, String filename, int chunkCount, User user) {
        this.docKey = docKey;
        this.filename = filename;
        this.chunkCount = chunkCount;
        this.user = user;
    }

    public Long getId() { return id; }
    public String getDocKey() { return docKey; }
    public String getFilename() { return filename; }
    public int getChunkCount() { return chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
}
