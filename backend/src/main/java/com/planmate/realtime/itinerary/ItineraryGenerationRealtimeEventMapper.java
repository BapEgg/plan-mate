package com.planmate.realtime.itinerary;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationRealtimeEventMapper {

    private final Clock clock;

    public ItineraryGenerationRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload> toEnvelope(
            ItineraryGenerationStatusChangedEvent event
    ) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.ITINERARY_GENERATION_STATUS_CHANGED,
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
