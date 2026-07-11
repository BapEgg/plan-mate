package com.planmate.itinerary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ItineraryDraftDay(
        @Positive int day,
        @NotEmpty List<@Valid ItineraryDraftItem> items
) {
}
