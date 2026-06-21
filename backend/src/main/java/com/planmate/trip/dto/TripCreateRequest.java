package com.planmate.trip.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TripCreateRequest(
        @NotBlank
        @Size(max = 60)
        String title,

        @NotBlank
        @Size(max = 60)
        String destination,

        @NotBlank
        @Size(max = 255)
        String destinationPlaceId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {

    @AssertTrue(message = "endDate must be on or after startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

}
