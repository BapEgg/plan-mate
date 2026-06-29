package com.planmate.itinerary.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationWorkerMetrics {

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_SKIPPED = "skipped";
    public static final String RESULT_FAILED = "failed";

    private static final String PROCESSED_METRIC = "planmate.itinerary.generation.worker.processed";
    private static final String RETRY_METRIC = "planmate.itinerary.generation.worker.retry";
    private static final String DURATION_METRIC = "planmate.itinerary.generation.worker.duration";

    private final MeterRegistry meterRegistry;

    public ItineraryGenerationWorkerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public void recordProcessed(String result, Timer.Sample sample) {
        Counter.builder(PROCESSED_METRIC)
                .description("Itinerary generation worker message processing count.")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
        sample.stop(Timer.builder(DURATION_METRIC)
                .description("Itinerary generation worker message processing duration.")
                .tag("result", result)
                .register(meterRegistry));
    }

    public void recordRetry() {
        Counter.builder(RETRY_METRIC)
                .description("Itinerary generation worker retry count.")
                .register(meterRegistry)
                .increment();
    }
}
