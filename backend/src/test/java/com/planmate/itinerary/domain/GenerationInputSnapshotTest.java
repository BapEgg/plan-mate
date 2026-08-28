package com.planmate.itinerary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationInputSnapshotTest {

    @Test
    void normalizesNullListsToEmptyLists() {
        GenerationInputSnapshot snapshot = new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination("place", "Kyoto", "address", 35.0, 135.0, null, null, "locality"),
                companion(),
                new GenerationInputSnapshot.Budget("KRW", 1000L, "BALANCED", null),
                new GenerationInputSnapshot.Preference("BALANCED", null),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", null),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", "DOWNTOWN", null, null, null, null, null, null, null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                null,
                null,
                null
        );

        assertThat(snapshot.destination().types()).isEmpty();
        assertThat(snapshot.budget().includedItems()).isEmpty();
        assertThat(snapshot.preference().interests()).isEmpty();
        assertThat(snapshot.transportation().secondaryModes()).isEmpty();
        assertThat(snapshot.accommodation().types()).isEmpty();
        assertThat(snapshot.mustVisitPlaces()).isEmpty();
        assertThat(snapshot.avoidConditions()).isEmpty();
    }

    @Test
    void defensivelyCopiesLists() {
        List<String> destinationTypes = new ArrayList<>(List.of("locality"));
        List<String> includedItems = new ArrayList<>(List.of("FOOD"));
        List<String> interests = new ArrayList<>(List.of("CAFE"));
        List<String> secondaryModes = new ArrayList<>(List.of("WALK"));
        List<String> accommodationTypes = new ArrayList<>(List.of("lodging"));
        List<String> mustVisitTypes = new ArrayList<>(List.of("tourist_attraction"));
        List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces = new ArrayList<>(List.of(
                new GenerationInputSnapshot.MustVisitPlace("must-1", "Place", "Address", 35.0, 135.0, mustVisitTypes, "tourist_attraction")
        ));
        List<String> avoidConditions = new ArrayList<>(List.of("LONG_WALK"));

        GenerationInputSnapshot snapshot = new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination("place", "Kyoto", "address", 35.0, 135.0, null, destinationTypes, "locality"),
                companion(),
                new GenerationInputSnapshot.Budget("KRW", 1000L, "BALANCED", includedItems),
                new GenerationInputSnapshot.Preference("BALANCED", interests),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", secondaryModes),
                new GenerationInputSnapshot.Accommodation("PLACE_SEARCH", null, "hotel", "Hotel", "Hotel address", 35.1, 135.1, accommodationTypes, "lodging", null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                mustVisitPlaces,
                avoidConditions,
                null
        );

        destinationTypes.add("political");
        includedItems.add("LODGING");
        interests.add("FOOD");
        secondaryModes.add("BUS");
        accommodationTypes.add("establishment");
        mustVisitTypes.add("point_of_interest");
        mustVisitPlaces.clear();
        avoidConditions.add("RAIN");

        assertThat(snapshot.destination().types()).containsExactly("locality");
        assertThat(snapshot.budget().includedItems()).containsExactly("FOOD");
        assertThat(snapshot.preference().interests()).containsExactly("CAFE");
        assertThat(snapshot.transportation().secondaryModes()).containsExactly("WALK");
        assertThat(snapshot.accommodation().types()).containsExactly("lodging");
        assertThat(snapshot.mustVisitPlaces()).hasSize(1);
        assertThat(snapshot.mustVisitPlaces().get(0).types()).containsExactly("tourist_attraction");
        assertThat(snapshot.avoidConditions()).containsExactly("LONG_WALK");

        assertThatThrownBy(() -> snapshot.mustVisitPlaces().add(snapshot.mustVisitPlaces().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void calculatesTripDayCountAndResolvedState() {
        GenerationInputSnapshot snapshot = new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination("place", "Kyoto", "address", 35.0, 135.0, null, List.of(), "locality"),
                companion(),
                new GenerationInputSnapshot.Budget("KRW", 1000L, "BALANCED", List.of()),
                new GenerationInputSnapshot.Preference("BALANCED", List.of()),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of()),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(
                        new GenerationInputSnapshot.MustVisitPlace("must-1", "Place", "Address", 35.0, 135.0, List.of(), null),
                        new GenerationInputSnapshot.MustVisitPlace("must-2", "Unresolved", null, null, null, List.of(), null)
                ),
                List.of(),
                null
        );

        assertThat(snapshot.tripDayCount()).isEqualTo(3);
        assertThat(snapshot.destination().isResolved()).isTrue();
        assertThat(snapshot.mustVisitPlaces().get(0).isResolved()).isTrue();
        assertThat(snapshot.mustVisitPlaces().get(1).isResolved()).isFalse();
    }

    private GenerationInputSnapshot.Companion companion() {
        return new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0);
    }
}
