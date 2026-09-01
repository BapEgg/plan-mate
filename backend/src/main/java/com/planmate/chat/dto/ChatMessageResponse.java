package com.planmate.chat.dto;

import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageType;
import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        String tripId,
        Long authorUserId,
        ChatMessageType type,
        String body,
        String clientMessageId,
        Instant sentAt
) {
    public static ChatMessageResponse from(ChatMessageEntity entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getTripId().toString(),
                entity.getAuthorUserId(),
                entity.getType(),
                entity.getBody(),
                entity.getClientMessageId(),
                entity.getSentAt()
        );
    }
}
