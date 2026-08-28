package com.planmate.place.api;

import java.util.List;

public record PlaceAutocompleteItem(
        String placeId,
        String mainText,
        String secondaryText,
        String displayText,
        List<String> types,
        String searchScope
) {

    public PlaceAutocompleteItem {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
