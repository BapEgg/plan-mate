package com.planmate.place.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceInDestinationAutocompleteRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String query,

        @NotBlank
        String destinationPlaceId,

        @Size(max = 10)
        String languageCode
) {
}
