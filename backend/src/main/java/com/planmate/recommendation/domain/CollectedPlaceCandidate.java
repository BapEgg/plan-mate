package com.planmate.recommendation.domain;

import com.planmate.place.dto.GeoPoint;
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
