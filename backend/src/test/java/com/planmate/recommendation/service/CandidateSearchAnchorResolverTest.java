package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import com.planmate.recommendation.domain.SearchAnchorType;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.ResolvedAccommodation;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateSearchAnchorResolverTest {

    private final CandidateSearchAnchorResolver resolver = new CandidateSearchAnchorResolver();

    @Test
    void undecidedAccommodationUsesDestinationAnchor() {
        ResolvedDestination destination = destination();
        TripPlanningProfileEntity profile = TripPlanningProfileEntity.create(
                null,
                request(AccommodationMode.UNDECIDED),
                null,
                List.of(),
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                Instant.now()
        );

        CandidateSearchAnchor anchor = resolver.resolve(destination, profile);

        assertThat(anchor.type()).isEqualTo(SearchAnchorType.DESTINATION);
        assertThat(anchor.location()).isEqualTo(destination.location());
        assertThat(anchor.viewport()).isEqualTo(destination.viewport());
    }

    @Test
    void selectedAccommodationUsesAccommodationLocationWithoutViewport() {
        ResolvedDestination destination = destination();
        TripPlanningProfileEntity profile = TripPlanningProfileEntity.create(
                null,
                request(AccommodationMode.PLACE_SEARCH),
                new ResolvedAccommodation(
                        "accommodation-place",
                        "Dormy Inn",
                        "address",
                        33.5902,
                        130.4206,
                        List.of("lodging"),
                        "lodging"
                ),
                List.of(),
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                Instant.now()
        );

        CandidateSearchAnchor anchor = resolver.resolve(destination, profile);

        assertThat(anchor.type()).isEqualTo(SearchAnchorType.ACCOMMODATION);
        assertThat(anchor.location()).isEqualTo(new GeoPoint(33.5902, 130.4206));
        assertThat(anchor.viewport()).isNull();
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

    private TripCreateRequest request(AccommodationMode accommodationMode) {
        TripCreateRequest.AccommodationRequest accommodation = accommodationMode == AccommodationMode.UNDECIDED
                ? new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, null, null, null, null)
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
}
