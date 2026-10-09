package com.example.ai_chatbot.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String conversationId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Conversation() {
    }

    public Conversation(String conversationId, User user) {
        this.conversationId = conversationId;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}