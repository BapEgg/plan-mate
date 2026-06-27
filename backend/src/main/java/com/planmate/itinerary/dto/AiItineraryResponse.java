package com.planmate.itinerary.dto;

import java.time.LocalDate;
import java.util.List;

public record AiItineraryResponse(
        String generationId,
        String summary,
        List<Day> days
) {

    public record Day(
            int day,
            LocalDate date,
            List<Item> items
    ) {
    }

    public record Item(
            int sequence,
            String placeId,
            String placeName,
            String startTime,
            int durationMinutes,
            String reason
    ) {
    }
}
