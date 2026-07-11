package com.planmate.common.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEventEnvelope<T>(
        String eventId,
        int schemaVersion,
        String type,
        String tripId,
        Instant occurredAt,
        T payload
) {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public static <T> RealtimeEventEnvelope<T> create(
            String type,
            Long tripId,
            Instant occurredAt,
            T payload
    ) {
        return new RealtimeEventEnvelope<>(
                UUID.randomUUID().toString(),
                CURRENT_SCHEMA_VERSION,
                type,
                tripId.toString(),
                occurredAt,
                payload
        );
    }
}
