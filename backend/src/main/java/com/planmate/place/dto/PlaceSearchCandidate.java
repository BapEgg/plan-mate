package com.planmate.place.dto;

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
}
