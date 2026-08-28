package com.planmate.itinerary.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationPipelineMetrics {

    private static final String CANDIDATE_COUNT_METRIC = "planmate.itinerary.generation.candidates";

    private final DistributionSummary candidateCount;

    public ItineraryGenerationPipelineMetrics(MeterRegistry meterRegistry) {
        candidateCount = DistributionSummary.builder(CANDIDATE_COUNT_METRIC)
                .description("Candidate count of an itinerary generation successfully transitioned to ready.")
                .register(meterRegistry);
    }

    public void recordCandidateCount(int count) {
        candidateCount.record(count);
    }
}
