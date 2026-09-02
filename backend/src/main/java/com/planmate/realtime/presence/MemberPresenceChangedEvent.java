package com.planmate.realtime.presence;

import java.time.Instant;

public record MemberPresenceChangedEvent(
        Long tripId,
        Long memberId,
        PresenceStatus status,
        Instant changedAtUtc,
        long eventSequence
) {
}
