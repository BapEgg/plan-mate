package com.planmate.itinerary.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItineraryGenerationRealtimeEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final ItineraryGenerationRealtimeEventMapper mapper = new ItineraryGenerationRealtimeEventMapper(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void mapsStatusChangedEventToRealtimeEnvelope() {
        ItineraryGenerationStatusChangedEvent event = new ItineraryGenerationStatusChangedEvent(
                45L,
                123L,
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                ItineraryGenerationStatus.READY_FOR_PLANNING,
                120,
                null,
                NOW.minusSeconds(3)
        );

        RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload> envelope = mapper.toEnvelope(event);

        assertThat(UUID.fromString(envelope.eventId())).isNotNull();
        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.type()).isEqualTo("ITINERARY_GENERATION_STATUS_CHANGED");
        assertThat(envelope.tripId()).isEqualTo("45");
        assertThat(envelope.occurredAt()).isEqualTo(NOW);
        assertThat(envelope.payload())
                .extracting(
                        ItineraryGenerationStatusChangedPayload::generationId,
                        ItineraryGenerationStatusChangedPayload::previousStatus,
                        ItineraryGenerationStatusChangedPayload::status,
                        ItineraryGenerationStatusChangedPayload::candidateCount,
                        ItineraryGenerationStatusChangedPayload::failureReason,
                        ItineraryGenerationStatusChangedPayload::updatedAt
                )
                .containsExactly(
                        "123",
                        ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                        ItineraryGenerationStatus.READY_FOR_PLANNING,
                        120L,
                        null,
                        NOW.minusSeconds(3)
                );
    }
}
