package com.planmate.realtime.itinerary;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;

public record ItineraryGenerationStatusChangedPayload(
        String generationId,
        ItineraryGenerationStatus previousStatus,
        ItineraryGenerationStatus status,
        long candidateCount,
        String failureReason,
        Instant updatedAt
) {
}
