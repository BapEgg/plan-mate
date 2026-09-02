package com.planmate.chat.dto;

import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageType;
import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        String tripId,
        Long authorUserId,
        ChatMessageType type,
        String body,
        String clientMessageId,
        Instant sentAt,
        ChatReplyPreviewResponse replyTo,
        boolean deleted,
        Instant deletedAt,
        Instant deletableUntil,
        List<ChatReactionSummaryResponse> reactions,
        List<ChatMentionResponse> mentions
) {
    public static ChatMessageResponse from(ChatMessageEntity entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getTripId().toString(),
                entity.getAuthorUserId(),
                entity.getType(),
                entity.isDeleted() ? "삭제된 메시지입니다." : entity.getBody(),
                entity.getClientMessageId(),
                entity.getSentAt(),
                null,
                entity.isDeleted(),
                entity.getDeletedAt(),
                entity.getSentAt().plusSeconds(300),
                List.of(),
                List.of()
        );
    }
}
