package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.PlaceSearchCandidate;
import com.planmate.place.dto.PlaceTextSearchRequest;
import com.planmate.place.dto.PlaceTextSearchResponse;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.ResolvedAccommodation;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceCandidateCollectionServiceTest {

    @Test
    void undecidedAccommodationUsesDestinationSearchArea() {
        GooglePlacesService googlePlacesService = mock(GooglePlacesService.class);
        List<PlaceTextSearchRequest> requests = new ArrayList<>();
        given(googlePlacesService.searchText(any()))
                .willAnswer(invocation -> {
                    PlaceTextSearchRequest request = invocation.getArgument(0);
                    requests.add(request);
                    return new PlaceTextSearchResponse(List.of(), null);
                });
        PlaceCandidateCollectionService service = service(googlePlacesService);
        ResolvedDestination destination = destination();

        service.collect(destination, profile(AccommodationMode.UNDECIDED, null));

        assertThat(requests).isNotEmpty();
        assertThat(requests.getFirst().searchArea().center()).isEqualTo(destination.location());
        assertThat(requests.getFirst().searchArea().viewport()).isEqualTo(destination.viewport());
    }

    @Test
    void selectedAccommodationUsesAccommodationCircleButDestinationQuery() {
        GooglePlacesService googlePlacesService = mock(GooglePlacesService.class);
        List<PlaceTextSearchRequest> requests = new ArrayList<>();
        given(googlePlacesService.searchText(any()))
                .willAnswer(invocation -> {
                    PlaceTextSearchRequest request = invocation.getArgument(0);
                    requests.add(request);
                    return new PlaceTextSearchResponse(List.of(
                            candidate("candidate-1", new GeoPoint(33.5902, 130.4206))
                    ), null);
                });
        PlaceCandidateCollectionService service = service(googlePlacesService);

        List<CollectedPlaceCandidate> candidates = service.collect(
                destination(),
                profile(AccommodationMode.PLACE_SEARCH, new ResolvedAccommodation(
                        "accommodation-place",
                        "Dormy Inn",
                        "address",
                        33.5902,
                        130.4206,
                        List.of("lodging"),
                        "lodging"
                ))
        );

        assertThat(requests).isNotEmpty();
        assertThat(requests.getFirst().textQuery()).contains("Kyoto");
        assertThat(requests.getFirst().searchArea().center()).isEqualTo(new GeoPoint(33.5902, 130.4206));
        assertThat(requests.getFirst().searchArea().viewport()).isNull();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().distanceMeters()).isEqualTo(0.0);
    }

    @Test
    void mustVisitPlacesAreForcedToCandidateFront() {
        GooglePlacesService googlePlacesService = mock(GooglePlacesService.class);
        given(googlePlacesService.searchText(any()))
                .willReturn(new PlaceTextSearchResponse(List.of(
                        candidate("searched-place", new GeoPoint(35.0116, 135.7681))
                ), null));
        PlaceCandidateCollectionService service = service(googlePlacesService);

        List<CollectedPlaceCandidate> candidates = service.collect(
                destination(),
                profile(AccommodationMode.UNDECIDED, null, List.of(new MustVisitPlaceSnapshot(
                        "must-place",
                        "Must Temple",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        List.of("tourist_attraction"),
                        "tourist_attraction"
                )))
        );

        assertThat(candidates).extracting(CollectedPlaceCandidate::placeId).contains("must-place");
        assertThat(candidates.getFirst().placeId()).isEqualTo("must-place");
        assertThat(candidates.getFirst().sourceCategories()).contains(com.planmate.recommendation.domain.CandidateSearchCategory.MUST_VISIT);
    }

    private PlaceCandidateCollectionService service(GooglePlacesService googlePlacesService) {
        return new PlaceCandidateCollectionService(
                googlePlacesService,
                new CandidateCategoryWeightCalculator(),
                new CandidateSearchQueryFactory(),
                new HaversineDistanceCalculator(),
                new PlaceCandidateAccumulator(),
                new CandidateScorer(),
                new CandidateSelector(new CandidateQuotaCalculator()),
                new CandidateSearchAnchorResolver(),
                5,
                20,
                20,
                50_000
        );
    }

    private ResolvedDestination destination() {
        return new ResolvedDestination(
                "destination-place",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0116, 135.7681),
                new GeoViewport(new GeoPoint(34.8, 135.5), new GeoPoint(35.2, 136.0)),
                List.of("locality"),
                "locality"
        );
    }

    private TripPlanningProfileEntity profile(
            AccommodationMode accommodationMode,
            ResolvedAccommodation accommodation
    ) {
        return profile(accommodationMode, accommodation, List.of());
    }

    private TripPlanningProfileEntity profile(
            AccommodationMode accommodationMode,
            ResolvedAccommodation accommodation,
            List<MustVisitPlaceSnapshot> mustVisitPlaces
    ) {
        return TripPlanningProfileEntity.create(
                null,
                request(accommodationMode),
                accommodation,
                mustVisitPlaces,
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                Instant.now()
        );
    }

    private TripCreateRequest request(AccommodationMode accommodationMode) {
        TripCreateRequest.AccommodationRequest accommodation = accommodationMode == AccommodationMode.UNDECIDED
                ? new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, com.planmate.trip.domain.AccommodationArea.TRANSIT, null, null, null)
                : new TripCreateRequest.AccommodationRequest(AccommodationMode.PLACE_SEARCH, null, "accommodation-place", null, null);
        return new TripCreateRequest(
                "Trip",
                "destination-place",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 3),
                new TripCreateRequest.CompanionRequest(2, com.planmate.trip.domain.CompanionType.FRIENDS, false, 0, null, false, 0),
                new TripCreateRequest.BudgetRequest(com.planmate.trip.domain.CurrencyCode.KRW, null, com.planmate.trip.domain.BudgetLevel.BALANCED, List.of()),
                new TripCreateRequest.PreferenceRequest(com.planmate.trip.domain.TravelPace.BALANCED, List.of(com.planmate.trip.domain.TripInterest.FOOD)),
                new TripCreateRequest.TransportationRequest(com.planmate.trip.domain.TransportMode.PUBLIC_TRANSIT, List.of()),
                accommodation,
                new TripCreateRequest.SchedulePreferenceRequest(null, null),
                new TripCreateRequest.AdditionalRequest(List.of(), List.of(), null)
        );
    }

    private PlaceSearchCandidate candidate(String placeId, GeoPoint location) {
        return new PlaceSearchCandidate(
                placeId,
                "Candidate",
                "address",
                location,
                List.of("tourist_attraction"),
                "tourist_attraction",
                "OPERATIONAL",
                4.5,
                100,
                List.of()
        );
    }
}
