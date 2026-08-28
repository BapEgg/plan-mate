package com.planmate.recommendation.api;

import java.util.List;

public record CandidateRecommendationRequest(
        Destination destination,
        List<Interest> interests,
        Accommodation accommodation,
        List<MustVisitPlace> mustVisitPlaces
) {

    public CandidateRecommendationRequest {
        interests = interests == null ? List.of() : List.copyOf(interests);
        mustVisitPlaces = mustVisitPlaces == null ? List.of() : List.copyOf(mustVisitPlaces);
    }

    public record Destination(
            String displayName,
            Location location,
            Viewport viewport
    ) {
    }

    public record Location(
            double latitude,
            double longitude
    ) {
    }

    public record Viewport(
            Location low,
            Location high
    ) {
    }

    public record Accommodation(
            Location location
    ) {
    }

    public record MustVisitPlace(
            String placeId,
            String displayName,
            String formattedAddress,
            Location location,
            String primaryType,
            List<String> types
    ) {

        public MustVisitPlace {
            types = types == null ? List.of() : List.copyOf(types);
        }

        public boolean isResolved() {
            return placeId != null && !placeId.isBlank() && location != null;
        }
    }
}
