package com.planmate.recommendation.domain;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.PlaceSearchArea;

public record CandidateSearchAnchor(
        SearchAnchorType type,
        GeoPoint location,
        GeoViewport viewport
) {

    public static CandidateSearchAnchor destination(GeoPoint location, GeoViewport viewport) {
        return new CandidateSearchAnchor(SearchAnchorType.DESTINATION, location, viewport);
    }

    public static CandidateSearchAnchor accommodation(GeoPoint location) {
        return new CandidateSearchAnchor(SearchAnchorType.ACCOMMODATION, location, null);
    }

    public PlaceSearchArea searchArea() {
        return PlaceSearchArea.of(location, viewport);
    }
}
