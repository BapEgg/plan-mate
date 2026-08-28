package com.planmate.realtime.itinerary;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationRealtimeEventMapper {

    public static final String ITINERARY_GENERATION_STATUS_CHANGED = "ITINERARY_GENERATION_STATUS_CHANGED";

    private final Clock clock;

    public ItineraryGenerationRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload> toEnvelope(
            ItineraryGenerationStatusChangedEvent event
    ) {
        return RealtimeEventEnvelope.create(
                ITINERARY_GENERATION_STATUS_CHANGED,
                event.tripId(),
                Instant.now(clock),
                new ItineraryGenerationStatusChangedPayload(
                        event.generationId().toString(),
                        event.previousStatus(),
                        event.status(),
                        event.candidateCount(),
                        event.failureReason(),
                        event.updatedAt()
                )
        );
    }
}
