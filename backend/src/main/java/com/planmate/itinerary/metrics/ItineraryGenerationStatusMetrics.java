package com.planmate.itinerary.metrics;

import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationStatusMetrics implements MeterBinder {

    private static final Duration CREATED_STALE_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration COLLECTING_STALE_THRESHOLD = Duration.ofMinutes(15);

    private final ItineraryGenerationRepository generationRepository;
    private final Clock clock;

    public ItineraryGenerationStatusMetrics(
            ItineraryGenerationRepository generationRepository,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (ItineraryGenerationStatus status : ItineraryGenerationStatus.values()) {
            Gauge.builder("planmate.itinerary.generation.status", () -> countByStatus(status))
                    .description("Current itinerary generation count by status.")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("planmate.itinerary.generation.stale",
                        () -> countStale(ItineraryGenerationStatus.CREATED, CREATED_STALE_THRESHOLD))
                .description("Itinerary generation count that stayed in a processing status longer than expected.")
                .tag("status", ItineraryGenerationStatus.CREATED.name())
                .tag("threshold", "5m")
                .register(registry);
        Gauge.builder("planmate.itinerary.generation.stale",
                        () -> countStale(ItineraryGenerationStatus.COLLECTING_CANDIDATES, COLLECTING_STALE_THRESHOLD))
                .description("Itinerary generation count that stayed in a processing status longer than expected.")
                .tag("status", ItineraryGenerationStatus.COLLECTING_CANDIDATES.name())
                .tag("threshold", "15m")
                .register(registry);
    }

    private double countByStatus(ItineraryGenerationStatus status) {
        return generationRepository.countByStatus(status);
    }

    private double countStale(ItineraryGenerationStatus status, Duration threshold) {
        Instant cutoff = Instant.now(clock).minus(threshold);
        return generationRepository.countByStatusAndUpdatedAtBefore(status, cutoff);
    }
}
