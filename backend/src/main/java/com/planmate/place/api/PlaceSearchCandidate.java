package com.planmate.place.api;

import java.util.List;

public record PlaceSearchCandidate(
        String placeId,
        String displayName,
        String formattedAddress,
        GeoPoint location,
        List<String> types,
        String primaryType,
        String businessStatus,
        Double rating,
        Integer userRatingCount,
        List<String> openingPeriods
) {

    public PlaceSearchCandidate {
        types = types == null ? List.of() : List.copyOf(types);
        openingPeriods = openingPeriods == null ? List.of() : List.copyOf(openingPeriods);
    }
}
