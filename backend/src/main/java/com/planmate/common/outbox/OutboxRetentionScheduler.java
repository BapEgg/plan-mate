package com.planmate.common.outbox;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.outbox.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionScheduler.class);

    private final OutboxRetentionCleanupService cleanupService;
    private final OutboxRetentionProperties properties;
    private final Clock clock;

    public OutboxRetentionScheduler(
            OutboxRetentionCleanupService cleanupService,
            OutboxRetentionProperties properties,
            Clock clock
    ) {
        this.cleanupService = cleanupService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.retention.cleanup-interval:1h}")
    public void cleanup() {
        Instant cutoff = Instant.now(clock).minus(properties.getRetention());
        try {
            int deletedCount = cleanupService.deleteBatchBefore(cutoff, properties.getBatchSize());
            log.info("Outbox retention cleanup completed: cutoff={}, deletedCount={}", cutoff, deletedCount);
        } catch (RuntimeException exception) {
            log.warn("Outbox retention cleanup failed; it will retry on the next schedule: cutoff={}",
                    cutoff, exception);
        }
    }
}
