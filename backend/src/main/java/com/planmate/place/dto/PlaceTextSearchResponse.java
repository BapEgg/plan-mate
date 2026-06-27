package com.planmate.place.dto;

import java.util.List;

public record PlaceTextSearchResponse(
        List<PlaceSearchCandidate> places,
        String nextPageToken
) {
}
