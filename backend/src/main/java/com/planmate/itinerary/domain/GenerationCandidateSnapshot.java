package com.planmate.itinerary.domain;

import java.util.List;

public record GenerationCandidateSnapshot(
        int rank,
        String placeId,
        String displayName,
        String formattedAddress,
        Location location,
        String primaryType,
        List<String> types,
        String businessStatus,
        Double rating,
        Integer userRatingCount,
        List<String> openingPeriods,
        List<String> sourceCategories,
        boolean forcedMustVisit,
        Double distanceMeters,
        double score
) {

    public GenerationCandidateSnapshot {
        types = copyOrEmpty(types);
        openingPeriods = copyOrEmpty(openingPeriods);
        sourceCategories = copyOrEmpty(sourceCategories);
    }

    public boolean hasPlaceId() {
        return placeId != null && !placeId.isBlank();
    }

    public boolean hasLocation() {
        return location != null;
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Location(
            double latitude,
            double longitude
    ) {
    }
}
