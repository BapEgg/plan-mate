package com.planmate.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetentionPropertiesTest {

    @Test
    void hasOperationalDefaultValues() {
        OutboxRetentionProperties properties = new OutboxRetentionProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.getCleanupInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getBatchSize()).isEqualTo(1000);
    }
}
