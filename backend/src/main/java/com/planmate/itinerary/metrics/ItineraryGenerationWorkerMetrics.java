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
    private static final String FAILURE_ATTEMPT_METRIC = "planmate.itinerary.generation.worker.failure.attempt";
    private static final String CLAIM_METRIC = "planmate.itinerary.generation.worker.claim";
    private static final String FENCED_METRIC = "planmate.itinerary.generation.worker.fenced";
    private static final String RECOVERY_PUBLISH_METRIC = "planmate.itinerary.generation.worker.recovery.publish";
    private static final String DURATION_METRIC = "planmate.itinerary.generation.worker.duration";
    private static final String DELIVERY_METRIC = "planmate.itinerary.generation.worker.delivery";
    private static final String RELIABILITY_HOOK_METRIC = "planmate.itinerary.generation.worker.reliability.hook";

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

    public void recordRetry(String classification, String failureCode) {
        Counter.builder(RETRY_METRIC)
                .description("Itinerary generation worker retry count.")
                .tag("classification", classification)
                .tag("failureCode", failureCode)
                .register(meterRegistry)
                .increment();
    }

    public void recordFailureAttempt(String classification, String failureCode) {
        Counter.builder(FAILURE_ATTEMPT_METRIC)
                .description("Itinerary generation worker failed attempt count.")
                .tag("classification", classification)
                .tag("failureCode", failureCode)
                .register(meterRegistry)
                .increment();
    }

    public void recordClaim(long claimVersion) {
        Counter.builder(CLAIM_METRIC)
                .description("Itinerary generation worker collection claim count.")
                .tag("type", claimVersion > 1 ? "recovery" : "initial")
                .register(meterRegistry)
                .increment();
    }

    public void recordFenced(String operation) {
        Counter.builder(FENCED_METRIC)
                .description("Stale itinerary generation worker result blocked by claim fencing.")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    public void recordRecoveryPublished() {
        Counter.builder(RECOVERY_PUBLISH_METRIC)
                .description("Stale itinerary generation recovery message publish count.")
                .register(meterRegistry)
                .increment();
    }

    public void recordDelivery(boolean redelivered) {
        Counter.builder(DELIVERY_METRIC)
                .description("Itinerary generation worker message delivery count.")
                .tag("redelivered", Boolean.toString(redelivered))
                .register(meterRegistry)
                .increment();
    }

    public void recordReliabilityHook(String phase) {
        Counter.builder(RELIABILITY_HOOK_METRIC)
                .description("Reliability-test-only worker pause hook entry count.")
                .tag("phase", phase)
                .register(meterRegistry)
                .increment();
    }
}
