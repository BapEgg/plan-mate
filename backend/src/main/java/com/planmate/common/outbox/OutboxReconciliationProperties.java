package com.planmate.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADR-0004: realtime fan-out reconciliation 설정. {@code enabled}는 기본 false다 — 지금은
 * dispatched_at을 채우는 realtime event가 없어 켜면 모든 rows를 undispatched로 오인한다.
 * WP-B/D가 두 번째 realtime event type을 outbox에 연결할 때 함께 켠다.
 */
@Component
@ConfigurationProperties(prefix = "app.outbox.reconciliation")
public class OutboxReconciliationProperties {

    private boolean enabled = false;
    private Duration staleAfter = Duration.ofMinutes(5);
    private Duration scanInterval = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = requirePositive(staleAfter, "staleAfter");
    }

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = requirePositive(scanInterval, "scanInterval");
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
