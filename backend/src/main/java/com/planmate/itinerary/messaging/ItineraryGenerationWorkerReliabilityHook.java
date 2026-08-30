package com.planmate.itinerary.messaging;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationWorkerReliabilityHook {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationWorkerReliabilityHook.class);
    private static final String AFTER_DELIVERY_BEFORE_CLAIM = "after_delivery_before_claim";

    private final ItineraryGenerationWorkerProperties properties;
    private final ItineraryGenerationWorkerMetrics metrics;
    private final AtomicBoolean beforeClaimPauseAvailable = new AtomicBoolean(true);
    private final AtomicBoolean beforeAckPauseAvailable = new AtomicBoolean(true);

    public ItineraryGenerationWorkerReliabilityHook(
            ItineraryGenerationWorkerProperties properties,
            ItineraryGenerationWorkerMetrics metrics
    ) {
        this.properties = properties;
        this.metrics = metrics;
    }

    public void pauseAfterDeliveryBeforeClaim(
            ItineraryGenerationRequestedMessage message,
            boolean redelivered
    ) {
        Duration delay = properties.getReliabilityAfterDeliveryBeforeClaimDelay();
        if (delay.isZero() || !beforeClaimPauseAvailable.compareAndSet(true, false)) {
            return;
        }

        metrics.recordReliabilityHook(AFTER_DELIVERY_BEFORE_CLAIM);
        log.warn(
                "RELIABILITY_HOOK_AFTER_DELIVERY_BEFORE_CLAIM: generationId={}, tripId={}, "
                        + "redelivered={}, delayMs={}",
                message.generationId(),
                message.tripId(),
                redelivered,
                delay.toMillis()
        );
        pause(delay, "Reliability hook interrupted before DB claim");
    }

    public void pauseAfterCommitBeforeAck(
            ItineraryGenerationRequestedMessage message,
            boolean redelivered
    ) {
        Duration delay = properties.getReliabilityAfterCommitBeforeAckDelay();
        if (delay.isZero() || !beforeAckPauseAvailable.compareAndSet(true, false)) {
            return;
        }

        log.warn(
                "RELIABILITY_HOOK_AFTER_COMMIT_BEFORE_ACK: generationId={}, tripId={}, redelivered={}, delayMs={}",
                message.generationId(),
                message.tripId(),
                redelivered,
                delay.toMillis()
        );

        pause(delay, "Reliability hook interrupted before listener ACK");
    }

    private void pause(Duration delay, String interruptedMessage) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruptedMessage, exception);
        }
    }
}
