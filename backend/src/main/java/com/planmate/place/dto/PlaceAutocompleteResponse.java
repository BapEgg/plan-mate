package com.planmate.place.dto;

import java.util.List;

public record PlaceAutocompleteResponse(
        List<PlaceAutocompleteItemResponse> items
) {
}
