package com.planmate.realtime.chat;

import com.planmate.chat.entity.ChatMessageType;
import java.time.Instant;

public record ChatMessageSentPayload(
        Long messageId,
        String clientMessageId,
        Long authorUserId,
        ChatMessageType type,
        String body,
        Instant sentAt
) {
}
