package com.planmate.chat.dto;

public record SetChatTypingRequest(
        ChatTypingState state,
        String clientSessionId,
        String clientEventId
) {
}
