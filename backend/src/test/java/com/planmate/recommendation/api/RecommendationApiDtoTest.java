package com.planmate.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationApiDtoTest {

    @Test
    void requestNormalizesNullListsToEmptyLists() {
        CandidateRecommendationRequest request = new CandidateRecommendationRequest(
                destination(),
                null,
                null,
                null
        );

        assertThat(request.interests()).isEmpty();
        assertThat(request.mustVisitPlaces()).isEmpty();
    }

    @Test
    void requestDefensivelyCopiesLists() {
        List<Interest> interests = new ArrayList<>(List.of(Interest.FOOD));
        List<CandidateRecommendationRequest.MustVisitPlace> mustVisitPlaces = new ArrayList<>(List.of(mustVisitPlace()));

        CandidateRecommendationRequest request = new CandidateRecommendationRequest(
                destination(),
                interests,
                null,
                mustVisitPlaces
        );
        interests.add(Interest.CAFE);
        mustVisitPlaces.clear();

        assertThat(request.interests()).containsExactly(Interest.FOOD);
        assertThat(request.mustVisitPlaces()).hasSize(1);
        assertThatThrownBy(() -> request.interests().add(Interest.CAFE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recommendedCandidateNormalizesNullListsToEmptyLists() {
        RecommendedPlaceCandidate candidate = new RecommendedPlaceCandidate(
                1,
                "place-1",
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "tourist_attraction",
                null,
                "OPERATIONAL",
                null,
                null,
                null,
                null,
                false,
                0,
                10
        );

        assertThat(candidate.types()).isEmpty();
        assertThat(candidate.openingPeriods()).isEmpty();
        assertThat(candidate.sourceCategories()).isEmpty();
    }

    @Test
    void recommendedCandidateDefensivelyCopiesLists() {
        List<String> types = new ArrayList<>(List.of("museum"));
        List<String> openingPeriods = new ArrayList<>(List.of("Mon 09:00-18:00"));
        List<String> categories = new ArrayList<>(List.of("CORE_VISIT"));

        RecommendedPlaceCandidate candidate = new RecommendedPlaceCandidate(
                1,
                "place-1",
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "museum",
                types,
                "OPERATIONAL",
                4.5,
                100,
                openingPeriods,
                categories,
                false,
                10,
                20
        );
        types.add("park");
        openingPeriods.clear();
        categories.add("MEAL");

        assertThat(candidate.types()).containsExactly("museum");
        assertThat(candidate.openingPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(candidate.sourceCategories()).containsExactly("CORE_VISIT");
        assertThatThrownBy(() -> candidate.sourceCategories().add("MEAL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void interestDefinesAllSupportedCodes() {
        assertThat(Interest.values())
                .extracting(Enum::name)
                .containsExactly(
                        "FOOD",
                        "SIGHTSEEING",
                        "CAFE",
                        "CULTURE",
                        "NATURE",
                        "SHOPPING",
                        "PHOTO",
                        "NIGHT_VIEW",
                        "ACTIVITY",
                        "REST",
                        "ART",
                        "THEME_PARK",
                        "LOCAL"
                );
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

    private CandidateRecommendationRequest.MustVisitPlace mustVisitPlace() {
        return new CandidateRecommendationRequest.MustVisitPlace(
                "must-place",
                "Must Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "tourist_attraction",
                List.of("tourist_attraction")
        );
    }
}
