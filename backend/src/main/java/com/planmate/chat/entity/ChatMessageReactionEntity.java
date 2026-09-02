package com.planmate.chat.entity;

import com.planmate.user.entity.UserEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "chat_message_reactions",
        uniqueConstraints = @UniqueConstraint(
                name = "chat_message_reactions_message_user_unique",
                columnNames = {"message_id", "user_id"}
        )
)
public class ChatMessageReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessageEntity message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private ChatReactionType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageReactionEntity() {
    }

    private ChatMessageReactionEntity(ChatMessageEntity message, UserEntity user, ChatReactionType type, Instant createdAt) {
        this.message = message;
        this.user = user;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static ChatMessageReactionEntity create(
            ChatMessageEntity message,
            UserEntity user,
            ChatReactionType type,
            Instant now
    ) {
        return new ChatMessageReactionEntity(message, user, type, now);
    }

    public void changeType(ChatReactionType type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public ChatMessageEntity getMessage() {
        return message;
    }

    public UserEntity getUser() {
        return user;
    }

    public ChatReactionType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
