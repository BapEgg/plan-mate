package com.planmate.chat.dto;

public record ChatMentionResponse(
        Long memberId,
        String displayNameSnapshot,
        int startCodePoint,
        int endCodePoint
) {
}
