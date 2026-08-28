package com.planmate.itinerary.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ItineraryGenerationPipelineMetricsTest {

    @Test
    void recordsAppliedCandidateCountAsDistributionSummary() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ItineraryGenerationPipelineMetrics metrics = new ItineraryGenerationPipelineMetrics(registry);

        metrics.recordCandidateCount(120);

        DistributionSummary summary = registry.get("planmate.itinerary.generation.candidates").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(120);
        assertThat(summary.getId().getTags()).isEmpty();
    }
}
