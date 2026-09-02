package com.planmate.chat.dto;

import java.util.List;

public record SendChatMessageRequest(
        String clientMessageId,
        String body,
        Long replyToMessageId,
        List<ChatMentionRequest> mentions
) {
}
