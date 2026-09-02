package com.planmate.realtime.regeneration;

public record RegenerationChangedPayload(
        Long regenerationId,
        Long generationId,
        String status,
        Long appliedItineraryId
) {
}
