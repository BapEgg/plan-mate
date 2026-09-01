package com.planmate.common.outbox;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxReconciliationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:10:00Z");

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxReconciliationProperties properties = new OutboxReconciliationProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final OutboxReconciliationScheduler scheduler =
            new OutboxReconciliationScheduler(outboxEventRepository, properties, clock);

    @Test
    void queriesUsingConfiguredStaleAfterCutoff() {
        properties.setStaleAfter(Duration.ofMinutes(5));
        given(outboxEventRepository.countByDispatchedAtIsNullAndCreatedAtBefore(NOW.minus(Duration.ofMinutes(5))))
                .willReturn(0L);

        scheduler.reportUndispatched();

        verify(outboxEventRepository).countByDispatchedAtIsNullAndCreatedAtBefore(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void doesNotThrowWhenUndispatchedRowsExist() {
        given(outboxEventRepository.countByDispatchedAtIsNullAndCreatedAtBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(3L);

        scheduler.reportUndispatched();
    }
}
