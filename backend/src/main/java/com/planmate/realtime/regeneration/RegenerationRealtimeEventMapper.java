package com.planmate.realtime.regeneration;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import com.planmate.regeneration.api.event.ItineraryRegenerationChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RegenerationRealtimeEventMapper {

    private final Clock clock;

    public RegenerationRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<RegenerationChangedPayload> toEnvelope(ItineraryRegenerationChangedEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.ITINERARY_REGENERATION_CHANGED,
                event.tripId(),
                Instant.now(clock),
                new RegenerationChangedPayload(
                        event.regenerationId(), event.generationId(), event.status(), event.appliedItineraryId()
                )
        );
    }
}
