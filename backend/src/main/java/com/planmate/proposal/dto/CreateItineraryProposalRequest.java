package com.planmate.proposal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;

public record CreateItineraryProposalRequest(
        @NotNull @Positive Long baseItineraryId,
        @Min(1) int baseItineraryVersion,
        @Min(1) int dayNumber,
        @NotNull @Positive Long targetItemId,
        @NotBlank String replacementPlaceId,
        @NotNull LocalTime replacementStartTime,
        @Min(15) @Max(720) int replacementDurationMinutes
) {
}
