package com.planmate.itinerary.dto;

import com.planmate.itinerary.api.ItineraryGenerationStatus;

public record ItineraryGenerationCreateResponse(
        String generationId,
        ItineraryGenerationStatus status,
        long candidateCount
) {
}
