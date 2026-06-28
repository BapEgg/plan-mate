package com.planmate.itinerary.messaging;

public record ItineraryGenerationRequestedMessage(
        Long generationId,
        Long tripId,
        Long userId
) {
}
