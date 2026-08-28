package com.planmate.itinerary.service;

import java.time.LocalTime;
import java.util.List;

record NormalizedAiItineraryDraft(
        Long generationId,
        List<Day> days
) {

    NormalizedAiItineraryDraft {
        days = days == null ? List.of() : List.copyOf(days);
    }

    record Day(
            int day,
            List<Item> items
    ) {

        Day {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record Item(
            int sequence,
            String placeId,
            LocalTime startTime,
            int durationMinutes
    ) {
    }
}
