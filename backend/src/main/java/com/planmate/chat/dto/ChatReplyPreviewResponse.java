package com.planmate.chat.dto;

public record ChatReplyPreviewResponse(
        Long messageId,
        Long authorUserId,
        String body,
        boolean deleted
) {
}
