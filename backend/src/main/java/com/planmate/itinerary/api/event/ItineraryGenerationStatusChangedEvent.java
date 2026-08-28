package com.planmate.itinerary.api.event;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;

public record ItineraryGenerationStatusChangedEvent(
        Long tripId,
        Long generationId,
        ItineraryGenerationStatus previousStatus,
        ItineraryGenerationStatus status,
        long candidateCount,
        String failureReason,
        Instant updatedAt
) {
}
