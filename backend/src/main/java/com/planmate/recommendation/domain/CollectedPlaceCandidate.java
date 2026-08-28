package com.planmate.recommendation.domain;

import com.planmate.place.api.GeoPoint;
import java.util.List;

public record CollectedPlaceCandidate(
        String placeId,
        String name,
        String address,
        GeoPoint location,
        String primaryType,
        List<String> types,
        String businessStatus,
        Double rating,
        Integer userRatingCount,
        List<String> openingPeriods,
        List<CandidateSearchCategory> sourceCategories,
        double distanceMeters,
        double score
) {

    public CollectedPlaceCandidate {
        types = types == null ? List.of() : List.copyOf(types);
        openingPeriods = openingPeriods == null ? List.of() : List.copyOf(openingPeriods);
        sourceCategories = sourceCategories == null ? List.of() : List.copyOf(sourceCategories);
    }

    public CollectedPlaceCandidate withScore(double score) {
        return new CollectedPlaceCandidate(
                placeId,
                name,
                address,
                location,
                primaryType,
                types,
                businessStatus,
                rating,
                userRatingCount,
                openingPeriods,
                sourceCategories,
                distanceMeters,
                score
        );
    }
}
