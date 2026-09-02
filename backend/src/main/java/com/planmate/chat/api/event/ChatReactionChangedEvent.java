package com.planmate.chat.api.event;

public record ChatReactionChangedEvent(Long tripId, Long messageId) {
}
