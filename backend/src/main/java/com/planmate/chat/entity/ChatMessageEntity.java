package com.planmate.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageType type;

    @Column(nullable = false)
    private String body;

    @Column(name = "client_message_id", nullable = false)
    private String clientMessageId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected ChatMessageEntity() {
    }

    private ChatMessageEntity(Long tripId, Long authorUserId, ChatMessageType type, String body, String clientMessageId, Instant sentAt) {
        this.tripId = tripId;
        this.authorUserId = authorUserId;
        this.type = type;
        this.body = body;
        this.clientMessageId = clientMessageId;
        this.sentAt = sentAt;
    }

    public static ChatMessageEntity userText(Long tripId, Long authorUserId, String body, String clientMessageId, Instant now) {
        return new ChatMessageEntity(tripId, authorUserId, ChatMessageType.USER_TEXT, body, clientMessageId, now);
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public ChatMessageType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
