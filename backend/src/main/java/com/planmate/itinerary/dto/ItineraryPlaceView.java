package com.planmate.itinerary.dto;

import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import java.time.LocalTime;

public record ItineraryPlaceView(
        Long itineraryId,
        Long itemId,
        int dayNo,
        int sequence,
        String placeId,
        LocalTime startTime,
        int durationMinutes,
        ItineraryItemCreatedSource createdSource,
        ItineraryPlaceDisplayView display
) {
}
