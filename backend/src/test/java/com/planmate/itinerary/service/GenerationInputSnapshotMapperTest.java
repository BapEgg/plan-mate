package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.trip.api.TripPlanningSnapshot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationInputSnapshotMapperTest {

    private final GenerationInputSnapshotMapper mapper = new GenerationInputSnapshotMapper();

    @Test
    void mapsAllFieldsFromTripPlanningSnapshot() {
        GenerationInputSnapshot result = mapper.map(snapshot(
                new TripPlanningSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                List.of("FOOD", "SIGHTSEEING"),
                List.of(mustVisitPlace("must-1")),
                accommodation()
        ));

        assertThat(result.tripId()).isEqualTo(45L);
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(result.destination()).satisfies(destination -> {
            assertThat(destination.placeId()).isEqualTo("place-kyoto");
            assertThat(destination.displayName()).isEqualTo("Kyoto");
            assertThat(destination.formattedAddress()).isEqualTo("Kyoto, Japan");
            assertThat(destination.latitude()).isEqualTo(35.0);
            assertThat(destination.longitude()).isEqualTo(135.0);
            assertThat(destination.viewport().lowLatitude()).isEqualTo(34.8);
            assertThat(destination.types()).containsExactly("locality");
            assertThat(destination.primaryType()).isEqualTo("locality");
        });
        assertThat(result.companion()).satisfies(companion -> {
            assertThat(companion.companionCount()).isEqualTo(3);
            assertThat(companion.companionType()).isEqualTo("FRIENDS");
            assertThat(companion.hasChildren()).isTrue();
            assertThat(companion.childCount()).isEqualTo(1);
            assertThat(companion.childAgeGroup()).isEqualTo("CHILD");
            assertThat(companion.hasSeniors()).isTrue();
            assertThat(companion.seniorCount()).isEqualTo(1);
        });
        assertThat(result.budget().includedItems()).containsExactly("FOOD", "LODGING");
        assertThat(result.preference().interests()).containsExactly("FOOD", "SIGHTSEEING");
        assertThat(result.transportation().secondaryModes()).containsExactly("WALK");
        assertThat(result.accommodation()).satisfies(accommodation -> {
            assertThat(accommodation.accommodationMode()).isEqualTo("PLACE_SEARCH");
            assertThat(accommodation.placeId()).isEqualTo("hotel-place");
            assertThat(accommodation.types()).containsExactly("lodging");
            assertThat(accommodation.checkInTime()).isEqualTo(LocalTime.of(15, 0));
        });
        assertThat(result.dailyStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.dailyEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(result.mustVisitPlaces()).hasSize(1);
        assertThat(result.avoidConditions()).containsExactly("LONG_WALK");
        assertThat(result.freeRequest()).isEqualTo("Keep lunch flexible.");
    }

    @Test
    void mapsNullableViewportAndEmptyLists() {
        GenerationInputSnapshot result = mapper.map(snapshot(
                null,
                List.of(),
                List.of(),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", "DOWNTOWN", null, null, null, null, null, List.of(), null, null, null)
        ));

        assertThat(result.destination().viewport()).isNull();
        assertThat(result.preference().interests()).isEmpty();
        assertThat(result.mustVisitPlaces()).isEmpty();
        assertThat(result.accommodation().placeId()).isNull();
        assertThat(result.accommodation().types()).isEmpty();
    }

    private TripPlanningSnapshot snapshot(
            TripPlanningSnapshot.Viewport viewport,
            List<String> interests,
            List<TripPlanningSnapshot.MustVisitPlace> mustVisitPlaces,
            TripPlanningSnapshot.Accommodation accommodation
    ) {
        return new TripPlanningSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new TripPlanningSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        viewport,
                        List.of("locality"),
                        "locality"
                ),
                new TripPlanningSnapshot.Companion(3, "FRIENDS", true, 1, "CHILD", true, 1),
                new TripPlanningSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD", "LODGING")),
                new TripPlanningSnapshot.Preference("BALANCED", interests),
                new TripPlanningSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                accommodation,
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                mustVisitPlaces,
                List.of("LONG_WALK"),
                "Keep lunch flexible."
        );
    }

    private TripPlanningSnapshot.Accommodation accommodation() {
        return new TripPlanningSnapshot.Accommodation(
                "PLACE_SEARCH",
                null,
                "hotel-place",
                "Kyoto Hotel",
                "Hotel address",
                35.1,
                135.1,
                List.of("lodging"),
                "lodging",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
        );
    }

    private TripPlanningSnapshot.MustVisitPlace mustVisitPlace(String placeId) {
        return new TripPlanningSnapshot.MustVisitPlace(
                placeId,
                "Kiyomizu",
                "Kiyomizu address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }
}
