package com.planmate.place.api;

import java.util.List;

public record PlaceAutocompleteResult(
        List<PlaceAutocompleteItem> items
) {

    public PlaceAutocompleteResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
