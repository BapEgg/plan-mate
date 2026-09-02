package com.planmate.chat.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_message_mentions")
public class ChatMessageMentionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessageEntity message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private UserEntity mentionedUser;

    @Column(name = "display_name_snapshot", nullable = false, length = 100)
    private String displayNameSnapshot;

    @Column(name = "start_code_point", nullable = false)
    private int startCodePoint;

    @Column(name = "end_code_point", nullable = false)
    private int endCodePoint;

    protected ChatMessageMentionEntity() {
    }

    private ChatMessageMentionEntity(
            ChatMessageEntity message,
            UserEntity mentionedUser,
            String displayNameSnapshot,
            int startCodePoint,
            int endCodePoint
    ) {
        this.message = message;
        this.mentionedUser = mentionedUser;
        this.displayNameSnapshot = displayNameSnapshot;
        this.startCodePoint = startCodePoint;
        this.endCodePoint = endCodePoint;
    }

    public static ChatMessageMentionEntity create(
            ChatMessageEntity message,
            UserEntity mentionedUser,
            String displayNameSnapshot,
            int startCodePoint,
            int endCodePoint
    ) {
        return new ChatMessageMentionEntity(
                message, mentionedUser, displayNameSnapshot, startCodePoint, endCodePoint
        );
    }

    public ChatMessageEntity getMessage() {
        return message;
    }

    public UserEntity getMentionedUser() {
        return mentionedUser;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public int getStartCodePoint() {
        return startCodePoint;
    }

    public int getEndCodePoint() {
        return endCodePoint;
    }
}
