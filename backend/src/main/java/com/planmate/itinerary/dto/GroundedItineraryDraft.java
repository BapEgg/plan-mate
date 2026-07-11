package com.planmate.itinerary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GroundedItineraryDraft(
        @NotBlank String generationId,
        @NotEmpty List<@Valid ItineraryDraftDay> days
) {
}
