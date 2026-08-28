package com.planmate.place.api;

public interface PlaceAutocompleteQuery {

    PlaceAutocompleteResult autocomplete(String query, String languageCode);

    PlaceAutocompleteResult autocompleteAccommodation(String query, String destinationPlaceId, String languageCode);

    PlaceAutocompleteResult autocompleteInDestination(String query, String destinationPlaceId, String languageCode);
}
