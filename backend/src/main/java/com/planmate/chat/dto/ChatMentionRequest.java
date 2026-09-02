package com.planmate.chat.dto;

public record ChatMentionRequest(Long memberId, Integer startCodePoint, Integer endCodePoint) {
}
