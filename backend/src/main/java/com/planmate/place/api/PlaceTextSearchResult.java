package com.planmate.place.api;

import java.util.List;

public record PlaceTextSearchResult(
        List<PlaceSearchCandidate> places,
        String nextPageToken
) {

    public PlaceTextSearchResult {
        places = places == null ? List.of() : List.copyOf(places);
    }
}
