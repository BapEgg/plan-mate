package com.planmate.realtime.presence;

import java.time.Instant;

public record MemberPresenceChangedPayload(
        Long memberId,
        PresenceStatus status,
        Instant changedAtUtc,
        long eventSequence
) {
}
