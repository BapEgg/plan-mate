package com.planmate.itinerary.dto;

import java.time.LocalDate;
import java.util.List;

public record ItineraryDayResponse(
        Long id,
        int day,
        LocalDate date,
        List<ItineraryItemResponse> items
) {
}
