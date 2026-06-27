package com.planmate.itinerary.dto;

import java.time.LocalTime;

public record ItineraryItemResponse(
        Long id,
        int sequence,
        String placeId,
        String placeName,
        Double latitude,
        Double longitude,
        LocalTime startTime,
        int durationMinutes,
        String reason
) {
}
