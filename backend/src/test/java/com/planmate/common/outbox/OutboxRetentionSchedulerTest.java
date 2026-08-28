package com.planmate.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class OutboxRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");

    @Mock
    private OutboxRetentionCleanupService cleanupService;

    private OutboxRetentionProperties properties;
    private OutboxRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new OutboxRetentionProperties();
        scheduler = new OutboxRetentionScheduler(
                cleanupService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void calculatesCutoffFromCurrentClockAndConfiguredRetention() {
        properties.setRetention(Duration.ofDays(7));
        properties.setBatchSize(1000);

        scheduler.cleanup();

        verify(cleanupService).deleteBatchBefore(Instant.parse("2026-01-03T00:00:00Z"), 1000);
    }

    @Test
    void cleanupFailureDoesNotPreventNextScheduledAttempt() {
        Instant cutoff = Instant.parse("2026-01-03T00:00:00Z");
        doThrow(new IllegalStateException("database unavailable"))
                .doReturn(0)
                .when(cleanupService).deleteBatchBefore(cutoff, 1000);

        scheduler.cleanup();
        scheduler.cleanup();

        verify(cleanupService, times(2)).deleteBatchBefore(cutoff, 1000);
    }

    @Test
    void disabledPropertyDoesNotCreateSchedulerBean() {
        new ApplicationContextRunner()
                .withPropertyValues("app.outbox.retention.enabled=false")
                .withBean(OutboxRetentionCleanupService.class,
                        () -> mock(OutboxRetentionCleanupService.class))
                .withBean(OutboxRetentionProperties.class, OutboxRetentionProperties::new)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withUserConfiguration(OutboxRetentionScheduler.class)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxRetentionScheduler.class));
    }
}
