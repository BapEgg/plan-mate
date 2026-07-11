package com.planmate.itinerary.realtime;

import com.planmate.itinerary.entity.ItineraryGenerationStatus;
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
