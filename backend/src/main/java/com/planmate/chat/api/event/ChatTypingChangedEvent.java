package com.planmate.chat.api.event;

import java.time.Instant;

public record ChatTypingChangedEvent(
        Long tripId,
        Long memberId,
        boolean active,
        Instant expiresAtUtc,
        long eventSequence
) {
}
