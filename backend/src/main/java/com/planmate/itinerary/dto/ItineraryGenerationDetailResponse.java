package com.planmate.itinerary.dto;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;

public record ItineraryGenerationDetailResponse(
        String generationId,
        String tripId,
        ItineraryGenerationStatus status,
        String promptVersion,
        long candidateCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
