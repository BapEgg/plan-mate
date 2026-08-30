package com.planmate.itinerary.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ItineraryGenerationWorkerReliabilityHookTest {

    @Test
    void pausesOnlyFirstDeliveryBeforeClaimAndRecordsItsPhase() {
        ItineraryGenerationWorkerProperties properties = new ItineraryGenerationWorkerProperties();
        properties.setReliabilityAfterDeliveryBeforeClaimDelay(Duration.ofMillis(1));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ItineraryGenerationWorkerReliabilityHook hook = new ItineraryGenerationWorkerReliabilityHook(
                properties,
                new ItineraryGenerationWorkerMetrics(registry)
        );
        ItineraryGenerationRequestedMessage message =
                new ItineraryGenerationRequestedMessage(1L, 2L, 3L);

        hook.pauseAfterDeliveryBeforeClaim(message, false);
        hook.pauseAfterDeliveryBeforeClaim(message, false);

        assertThat(registry.get("planmate.itinerary.generation.worker.reliability.hook")
                .tag("phase", "after_delivery_before_claim")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void disabledHooksDoNotCreateReliabilityMetric() {
        ItineraryGenerationWorkerProperties properties = new ItineraryGenerationWorkerProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ItineraryGenerationWorkerReliabilityHook hook = new ItineraryGenerationWorkerReliabilityHook(
                properties,
                new ItineraryGenerationWorkerMetrics(registry)
        );

        hook.pauseAfterDeliveryBeforeClaim(new ItineraryGenerationRequestedMessage(1L, 2L, 3L), false);

        assertThat(registry.find("planmate.itinerary.generation.worker.reliability.hook").counter())
                .isNull();
    }
}
