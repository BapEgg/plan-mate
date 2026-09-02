package com.planmate.realtime.chat;

import java.time.Instant;

public record ChatTypingChangedPayload(
        Long memberId,
        boolean active,
        Instant expiresAtUtc,
        long eventSequence
) {
}
