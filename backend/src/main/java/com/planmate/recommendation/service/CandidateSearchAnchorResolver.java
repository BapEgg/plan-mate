package com.planmate.recommendation.service;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import org.springframework.stereotype.Component;

@Component
public class CandidateSearchAnchorResolver {

    public CandidateSearchAnchor resolve(
            CandidateRecommendationRequest.Destination destination,
            CandidateRecommendationRequest.Accommodation accommodation
    ) {
        if (accommodation != null && accommodation.location() != null) {
            return CandidateSearchAnchor.accommodation(toPoint(accommodation.location()));
        }

        return CandidateSearchAnchor.destination(
                destination == null ? null : toPoint(destination.location()),
                destination == null ? null : toViewport(destination.viewport())
        );
    }

    private GeoPoint toPoint(CandidateRecommendationRequest.Location location) {
        return location == null ? null : new GeoPoint(location.latitude(), location.longitude());
    }

    private GeoViewport toViewport(CandidateRecommendationRequest.Viewport viewport) {
        if (viewport == null || viewport.low() == null || viewport.high() == null) {
            return null;
        }
        return new GeoViewport(toPoint(viewport.low()), toPoint(viewport.high()));
    }
}
