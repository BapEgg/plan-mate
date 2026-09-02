package com.planmate.chat.api.event;

import java.time.Instant;

public record ChatMessageDeletedEvent(Long tripId, Long messageId, Instant deletedAt) {
}
