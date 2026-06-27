package com.planmate.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccommodationAutocompleteRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String query,

        @NotBlank
        String destinationPlaceId,

        @Size(max = 10)
        String languageCode
) {
}
