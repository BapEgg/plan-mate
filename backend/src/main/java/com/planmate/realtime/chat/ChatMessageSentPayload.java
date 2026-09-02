package com.planmate.realtime.chat;

import com.planmate.chat.entity.ChatMessageType;
import com.planmate.chat.dto.ChatMentionResponse;
import java.time.Instant;
import java.util.List;

public record ChatMessageSentPayload(
        Long messageId,
        String clientMessageId,
        Long authorUserId,
        ChatMessageType type,
        String body,
        Instant sentAt,
        Long replyToMessageId,
        Long replyAuthorUserId,
        String replyBody,
        boolean replyDeleted,
        List<ChatMentionResponse> mentions
) {
}
