package com.planmate.recommendation.api;

import java.util.List;

public record RecommendedPlaceCandidate(
        int rank,
        String placeId,
        String displayName,
        String formattedAddress,
        CandidateRecommendationRequest.Location location,
        String primaryType,
        List<String> types,
        String businessStatus,
        Double rating,
        Integer userRatingCount,
        List<String> openingPeriods,
        List<String> sourceCategories,
        boolean forcedMustVisit,
        double distanceMeters,
        double score
) {

    public RecommendedPlaceCandidate {
        types = types == null ? List.of() : List.copyOf(types);
        openingPeriods = openingPeriods == null ? List.of() : List.copyOf(openingPeriods);
        sourceCategories = sourceCategories == null ? List.of() : List.copyOf(sourceCategories);
    }
}
