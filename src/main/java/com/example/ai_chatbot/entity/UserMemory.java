package com.example.ai_chatbot.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String memoryKey;

    @Column(columnDefinition = "TEXT")
    private String memoryValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public UserMemory() {
    }

    public UserMemory(String memoryKey, String memoryValue, User user) {
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryValue() {
        return memoryValue;
    }

    public void setMemoryValue(String memoryValue) {
        this.memoryValue = memoryValue;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

