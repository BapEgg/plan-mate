package com.planmate.itinerary.dto;

import java.time.Instant;
import java.util.List;

public record ItineraryResponse(
        Long id,
        Long generationId,
        String summary,
        Instant createdAt,
        List<ItineraryDayResponse> days
) {
}
