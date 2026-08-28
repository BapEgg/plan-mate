package com.planmate.place.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceAutocompleteRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String query,

        @Size(max = 10)
        String languageCode
) {
}
