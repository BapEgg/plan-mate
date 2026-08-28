package com.planmate.itinerary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationCandidateSnapshotTest {

    @Test
    void listFieldsAreEmptyWhenNull() {
        GenerationCandidateSnapshot snapshot = new GenerationCandidateSnapshot(
                1,
                "place-1",
                "Place",
                "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "museum",
                null,
                "OPERATIONAL",
                4.5,
                100,
                null,
                null,
                false,
                120.0,
                42.5
        );

        assertThat(snapshot.types()).isEmpty();
        assertThat(snapshot.openingPeriods()).isEmpty();
        assertThat(snapshot.sourceCategories()).isEmpty();
    }

    @Test
    void listFieldsAreDefensivelyCopied() {
        List<String> types = new ArrayList<>(List.of("museum"));
        List<String> openingPeriods = new ArrayList<>(List.of("Mon 09:00-18:00"));
        List<String> sourceCategories = new ArrayList<>(List.of("CORE_VISIT"));

        GenerationCandidateSnapshot snapshot = new GenerationCandidateSnapshot(
                1,
                "place-1",
                "Place",
                "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "museum",
                types,
                "OPERATIONAL",
                4.5,
                100,
                openingPeriods,
                sourceCategories,
                false,
                120.0,
                42.5
        );
        types.add("point_of_interest");
        openingPeriods.clear();
        sourceCategories.add("MEAL");

        assertThat(snapshot.types()).containsExactly("museum");
        assertThat(snapshot.openingPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(snapshot.sourceCategories()).containsExactly("CORE_VISIT");
        assertThatThrownBy(() -> snapshot.types().add("park"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exposesSimplePresenceChecks() {
        GenerationCandidateSnapshot resolved = snapshot("place-1", new GenerationCandidateSnapshot.Location(35.0, 135.0));
        GenerationCandidateSnapshot blankPlaceId = snapshot(" ", new GenerationCandidateSnapshot.Location(35.0, 135.0));
        GenerationCandidateSnapshot missingLocation = snapshot("place-1", null);

        assertThat(resolved.hasPlaceId()).isTrue();
        assertThat(resolved.hasLocation()).isTrue();
        assertThat(blankPlaceId.hasPlaceId()).isFalse();
        assertThat(missingLocation.hasLocation()).isFalse();
    }

    private GenerationCandidateSnapshot snapshot(
            String placeId,
            GenerationCandidateSnapshot.Location location
    ) {
        return new GenerationCandidateSnapshot(
                1,
                placeId,
                "Place",
                "Address",
                location,
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                120.0,
                42.5
        );
    }
}
