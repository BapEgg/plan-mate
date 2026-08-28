package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceSearchCandidate;
import com.planmate.place.api.PlaceTextSearchQuery;
import com.planmate.place.api.PlaceTextSearchResult;
import com.planmate.place.api.PlaceTextSearcher;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.Interest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import com.planmate.recommendation.domain.PlaceTypePolicy;
import com.planmate.recommendation.domain.PlaceTypePolicyRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceCandidateCollectionServiceTest {

    @Test
    void missingAccommodationUsesDestinationSearchArea() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        List<PlaceTextSearchQuery> requests = new ArrayList<>();
        given(placeTextSearcher.searchText(any()))
                .willAnswer(invocation -> {
                    PlaceTextSearchQuery request = invocation.getArgument(0);
                    requests.add(request);
                    return new PlaceTextSearchResult(List.of(), null);
                });
        PlaceCandidateCollectionService service = service(placeTextSearcher);

        service.recommend(request(null, List.of(), List.of(Interest.FOOD)));

        assertThat(requests).isNotEmpty();
        assertThat(requests.getFirst().searchArea().center()).isEqualTo(new GeoPoint(35.0116, 135.7681));
        assertThat(requests.getFirst().searchArea().viewport()).isNotNull();
    }

    @Test
    void selectedAccommodationUsesAccommodationCircleButDestinationQuery() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        List<PlaceTextSearchQuery> requests = new ArrayList<>();
        given(placeTextSearcher.searchText(any()))
                .willAnswer(invocation -> {
                    PlaceTextSearchQuery request = invocation.getArgument(0);
                    requests.add(request);
                    return new PlaceTextSearchResult(List.of(
                            candidate("candidate-1", new GeoPoint(33.5902, 130.4206))
                    ), null);
                });
        PlaceCandidateCollectionService service = service(placeTextSearcher);

        List<RecommendedPlaceCandidate> candidates = service.recommend(request(
                new CandidateRecommendationRequest.Accommodation(
                        new CandidateRecommendationRequest.Location(33.5902, 130.4206)
                ),
                List.of(),
                List.of(Interest.FOOD)
        ));

        assertThat(requests).isNotEmpty();
        assertThat(requests.getFirst().textQuery()).contains("Kyoto");
        assertThat(requests.getFirst().searchArea().center()).isEqualTo(new GeoPoint(33.5902, 130.4206));
        assertThat(requests.getFirst().searchArea().viewport()).isNull();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().rank()).isEqualTo(1);
        assertThat(candidates.getFirst().distanceMeters()).isEqualTo(0.0);
    }

    @Test
    void mustVisitPlacesAreForcedToCandidateFront() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        given(placeTextSearcher.searchText(any()))
                .willReturn(new PlaceTextSearchResult(List.of(
                        candidate("searched-place", new GeoPoint(35.0116, 135.7681))
                ), null));
        PlaceCandidateCollectionService service = service(placeTextSearcher);

        List<RecommendedPlaceCandidate> candidates = service.recommend(request(
                null,
                List.of(new CandidateRecommendationRequest.MustVisitPlace(
                        "must-place",
                        "Must Temple",
                        "Kyoto, Japan",
                        new CandidateRecommendationRequest.Location(35.0, 135.0),
                        "tourist_attraction",
                        List.of("tourist_attraction")
                )),
                List.of(Interest.FOOD)
        ));

        assertThat(candidates).extracting(RecommendedPlaceCandidate::placeId).contains("must-place");
        assertThat(candidates.getFirst().placeId()).isEqualTo("must-place");
        assertThat(candidates.getFirst().rank()).isEqualTo(1);
        assertThat(candidates.getFirst().forcedMustVisit()).isTrue();
        assertThat(candidates.getFirst().sourceCategories()).contains("MUST_VISIT");
    }

    @Test
    void unresolvedMustVisitPlaceIsExcluded() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        given(placeTextSearcher.searchText(any())).willReturn(new PlaceTextSearchResult(List.of(), null));
        PlaceCandidateCollectionService service = service(placeTextSearcher);

        List<RecommendedPlaceCandidate> candidates = service.recommend(request(
                null,
                List.of(new CandidateRecommendationRequest.MustVisitPlace(
                        "must-place",
                        "Must Temple",
                        "Kyoto, Japan",
                        null,
                        "tourist_attraction",
                        List.of("tourist_attraction")
                )),
                List.of(Interest.FOOD)
        ));

        assertThat(candidates).isEmpty();
    }

    @Test
    void blockedTypePolicyExcludesCandidatesAndLoadsPoliciesOnce() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        given(placeTextSearcher.searchText(any()))
                .willReturn(new PlaceTextSearchResult(List.of(
                        candidate(
                                "blocked-place",
                                new GeoPoint(35.0116, 135.7681),
                                List.of("tourist_attraction"),
                                "tourist_attraction"
                        ),
                        candidate(
                                "allowed-place",
                                new GeoPoint(35.0117, 135.7682),
                                List.of("museum"),
                                "museum"
                        )
                ), null));
        PlaceTypePolicyService placeTypePolicyService = mock(PlaceTypePolicyService.class);
        given(placeTypePolicyService.loadEnabledPoliciesByTypeName())
                .willReturn(Map.of(
                        "tourist_attraction",
                        new PlaceTypePolicyRule(
                                "tourist_attraction",
                                PlaceTypePolicy.BLOCK,
                                0,
                                "blocked for test"
                        )
                ));
        PlaceCandidateCollectionService service = service(placeTextSearcher, placeTypePolicyService);

        List<RecommendedPlaceCandidate> candidates = service.recommend(request(null, List.of(), List.of(Interest.FOOD)));

        assertThat(candidates).extracting(RecommendedPlaceCandidate::placeId)
                .contains("allowed-place")
                .doesNotContain("blocked-place");
        verify(placeTypePolicyService, times(1)).loadEnabledPoliciesByTypeName();
    }

    @Test
    void responseRanksFinalOrderFromOne() {
        PlaceTextSearcher placeTextSearcher = mock(PlaceTextSearcher.class);
        given(placeTextSearcher.searchText(any()))
                .willReturn(new PlaceTextSearchResult(List.of(
                        candidate("place-1", new GeoPoint(35.0116, 135.7681)),
                        candidate("place-2", new GeoPoint(35.0117, 135.7682))
                ), null));
        PlaceCandidateCollectionService service = service(placeTextSearcher);

        List<RecommendedPlaceCandidate> candidates = service.recommend(request(null, List.of(), List.of(Interest.FOOD)));

        assertThat(candidates).isNotEmpty();
        assertThat(candidates)
                .extracting(RecommendedPlaceCandidate::rank)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, candidates.size()).boxed().toList());
        assertThatThrownBy(() -> candidates.add(candidates.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private PlaceCandidateCollectionService service(PlaceTextSearcher placeTextSearcher) {
        PlaceTypePolicyService placeTypePolicyService = mock(PlaceTypePolicyService.class);
        given(placeTypePolicyService.loadEnabledPoliciesByTypeName()).willReturn(Map.of());
        return service(placeTextSearcher, placeTypePolicyService);
    }

    private PlaceCandidateCollectionService service(
            PlaceTextSearcher placeTextSearcher,
            PlaceTypePolicyService placeTypePolicyService
    ) {
        return new PlaceCandidateCollectionService(
                placeTextSearcher,
                new CandidateCategoryWeightCalculator(),
                new CandidateSearchQueryFactory(),
                new HaversineDistanceCalculator(),
                new PlaceCandidateAccumulator(),
                new CandidateScorer(),
                new CandidateSelector(new CandidateQuotaCalculator()),
                new CandidateSearchAnchorResolver(),
                placeTypePolicyService,
                5,
                20,
                20,
                50_000
        );
    }

    private CandidateRecommendationRequest request(
            CandidateRecommendationRequest.Accommodation accommodation,
            List<CandidateRecommendationRequest.MustVisitPlace> mustVisitPlaces,
            List<Interest> interests
    ) {
        return new CandidateRecommendationRequest(
                destination(),
                interests,
                accommodation,
                mustVisitPlaces
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

    private PlaceSearchCandidate candidate(String placeId, GeoPoint location) {
        return candidate(placeId, location, List.of("tourist_attraction"), "tourist_attraction");
    }

    private PlaceSearchCandidate candidate(
            String placeId,
            GeoPoint location,
            List<String> types,
            String primaryType
    ) {
        return new PlaceSearchCandidate(
                placeId,
                "Candidate",
                "address",
                location,
                types,
                primaryType,
                "OPERATIONAL",
                4.5,
                100,
                List.of()
        );
    }
}
