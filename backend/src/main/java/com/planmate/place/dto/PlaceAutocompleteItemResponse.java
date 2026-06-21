package com.planmate.place.dto;

import java.util.List;

public record PlaceAutocompleteItemResponse(
        String placeId,
        String mainText,
        String secondaryText,
        String displayText,
        List<String> types,
        String searchScope
) {
}
