package com.planmate.regeneration.api.event;

public record ItineraryRegenerationChangedEvent(
        Long tripId,
        Long regenerationId,
        Long generationId,
        String status,
        Long appliedItineraryId
) {
}
