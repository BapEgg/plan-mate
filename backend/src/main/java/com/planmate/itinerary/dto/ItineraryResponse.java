package com.planmate.itinerary.dto;

import java.time.Instant;
import java.util.List;

public record ItineraryResponse(
        Long id,
        Long generationId,
        Instant createdAt,
        List<ItineraryDayResponse> days
) {
}
