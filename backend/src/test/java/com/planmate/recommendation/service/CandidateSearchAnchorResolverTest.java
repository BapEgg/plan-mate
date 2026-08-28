package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.place.api.GeoPoint;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import com.planmate.recommendation.domain.SearchAnchorType;
import org.junit.jupiter.api.Test;

class CandidateSearchAnchorResolverTest {

    private final CandidateSearchAnchorResolver resolver = new CandidateSearchAnchorResolver();

    @Test
    void missingAccommodationUsesDestinationAnchor() {
        CandidateRecommendationRequest.Destination destination = destination();

        CandidateSearchAnchor anchor = resolver.resolve(destination, null);

        assertThat(anchor.type()).isEqualTo(SearchAnchorType.DESTINATION);
        assertThat(anchor.location()).isEqualTo(new GeoPoint(35.0116, 135.7681));
        assertThat(anchor.viewport()).isNotNull();
        assertThat(anchor.viewport().low()).isEqualTo(new GeoPoint(34.8, 135.5));
    }

    @Test
    void selectedAccommodationUsesAccommodationLocationWithoutViewport() {
        CandidateRecommendationRequest.Accommodation accommodation = new CandidateRecommendationRequest.Accommodation(
                new CandidateRecommendationRequest.Location(33.5902, 130.4206)
        );

        CandidateSearchAnchor anchor = resolver.resolve(destination(), accommodation);

        assertThat(anchor.type()).isEqualTo(SearchAnchorType.ACCOMMODATION);
        assertThat(anchor.location()).isEqualTo(new GeoPoint(33.5902, 130.4206));
        assertThat(anchor.viewport()).isNull();
    }

    private CandidateRecommendationRequest.Destination destination() {
        return new CandidateRecommendationRequest.Destination(
                "Kyoto",
                new CandidateRecommendationRequest.Location(35.0116, 135.7681),
                new CandidateRecommendationRequest.Viewport(
                        new CandidateRecommendationRequest.Location(34.8, 135.5),
                        new CandidateRecommendationRequest.Location(35.2, 136.0)
                )
        );
    }
}
