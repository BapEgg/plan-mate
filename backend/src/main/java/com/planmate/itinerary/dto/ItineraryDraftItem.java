package com.planmate.itinerary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ItineraryDraftItem(
        @Positive int sequence,
        @NotBlank String placeId,
        @NotBlank String startTime,
        @Positive int durationMinutes
) {
}
