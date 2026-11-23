package com.carebridge.chat;

import com.carebridge.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 120)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected ChatMessage() {
    }

    public ChatMessage(
            String roomId,
            ChatRoomType roomType,
            User sender,
            Long contractId,
            String content
    ) {
        this.roomId = roomId;
        this.roomType = roomType;
        this.sender = sender;
        this.contractId = contractId;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public ChatRoomType getRoomType() {
        return roomType;
    }

    public User getSender() {
        return sender;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getContent() {
        return content;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
