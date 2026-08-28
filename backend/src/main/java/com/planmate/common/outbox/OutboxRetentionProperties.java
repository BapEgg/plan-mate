package com.planmate.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.outbox.retention")
public class OutboxRetentionProperties {

    private boolean enabled = true;
    private Duration retention = Duration.ofDays(7);
    private Duration cleanupInterval = Duration.ofHours(1);
    private int batchSize = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = requirePositive(retention, "retention");
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = requirePositive(cleanupInterval, "cleanupInterval");
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
