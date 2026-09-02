package com.planmate.realtime.chat;

import java.time.Instant;

public record ChatMessageChangedPayload(Long messageId, Instant deletedAt) {
}
