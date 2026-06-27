package com.planmate.itinerary.dto;

import com.planmate.itinerary.entity.ItineraryGenerationStatus;

public record ItineraryGenerationCreateResponse(
        String generationId,
        ItineraryGenerationStatus status,
        long candidateCount
) {
}
