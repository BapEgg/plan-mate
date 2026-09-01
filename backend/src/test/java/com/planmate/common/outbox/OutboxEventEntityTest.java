package com.planmate.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxEventEntityTest {

    @Test
    void markDispatchedRecordsTimestampAndIncrementsAttempts() {
        OutboxEventEntity event = OutboxEventEntity.create(
                "TRIP",
                "45",
                "TEST_EVENT",
                Map.of("key", "value"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertThat(event.getDispatchedAt()).isNull();
        assertThat(event.getDispatchAttempts()).isZero();

        event.markDispatched(Instant.parse("2026-01-01T00:00:05Z"));

        assertThat(event.getDispatchedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:05Z"));
        assertThat(event.getDispatchAttempts()).isEqualTo(1);

        event.markDispatched(Instant.parse("2026-01-01T00:00:10Z"));

        assertThat(event.getDispatchAttempts()).isEqualTo(2);
    }
}
