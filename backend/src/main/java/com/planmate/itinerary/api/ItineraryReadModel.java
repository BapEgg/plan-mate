package com.planmate.itinerary.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ItineraryReadModel(
        Long id,
        Long generationId,
        Instant createdAt,
        List<Day> days
) {

    public record Day(
            Long id,
            int day,
            LocalDate date,
            List<Item> items
    ) {
    }

    public record Item(
            Long id,
            int sequence,
            String placeId,
            LocalTime startTime,
            int durationMinutes,
            String createdSource
    ) {
    }
}
