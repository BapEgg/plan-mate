package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationCandidateSnapshotEntityTest {

    @Test
    void fromMapsSnapshotAndStoresCandidateRank() {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z"));
        GenerationCandidateSnapshot snapshot = snapshot(
                3,
                List.of("museum", "point_of_interest"),
                List.of("CORE_VISIT", "MEAL"),
                List.of("Mon 09:00-18:00")
        );

        GenerationCandidateSnapshotEntity entity = GenerationCandidateSnapshotEntity.from(generation, snapshot);

        assertThat(entity.getGeneration()).isSameAs(generation);
        assertThat(entity.getPlaceId()).isEqualTo("place-1");
        assertThat(entity.getName()).isEqualTo("Place");
        assertThat(entity.getAddress()).isEqualTo("Address");
        assertThat(entity.getLatitude()).isEqualTo(35.0);
        assertThat(entity.getLongitude()).isEqualTo(135.0);
        assertThat(entity.getPrimaryType()).isEqualTo("museum");
        assertThat(entity.getTypes()).containsExactly("museum", "point_of_interest");
        assertThat(entity.getBusinessStatus()).isEqualTo("OPERATIONAL");
        assertThat(entity.getRating()).isEqualTo(4.5);
        assertThat(entity.getUserRatingCount()).isEqualTo(100);
        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT", "MEAL");
        assertThat(entity.getOpeningPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(entity.isForcedMustVisit()).isFalse();
        assertThat(entity.getDistanceMeters()).isEqualTo(100.0);
        assertThat(entity.getScore()).isEqualTo(42.5);
        assertThat(entity.getRank()).isEqualTo(3);
    }

    @Test
    void sourceCategoriesCanStoreExistingEnumNameJsonValuesAsStrings() {
        GenerationCandidateSnapshotEntity entity = GenerationCandidateSnapshotEntity.from(
                ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z")),
                snapshot(1, List.of(), List.of("CORE_VISIT", "MEAL", "MUST_VISIT"), List.of())
        );

        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT", "MEAL", "MUST_VISIT");
        assertThat(entity.isForcedMustVisit()).isTrue();
    }

    @Test
    void listFieldsAreDefensivelyCopied() {
        List<String> types = new ArrayList<>(List.of("museum"));
        List<String> sourceCategories = new ArrayList<>(List.of("CORE_VISIT"));
        List<String> openingPeriods = new ArrayList<>(List.of("Mon 09:00-18:00"));

        GenerationCandidateSnapshotEntity entity = GenerationCandidateSnapshotEntity.from(
                ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z")),
                snapshot(1, types, sourceCategories, openingPeriods)
        );
        types.add("point_of_interest");
        sourceCategories.add("MEAL");
        openingPeriods.clear();

        assertThat(entity.getTypes()).containsExactly("museum");
        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT");
        assertThat(entity.getOpeningPeriods()).containsExactly("Mon 09:00-18:00");
        assertThatThrownBy(() -> entity.getSourceCategories().add("MEAL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private GenerationCandidateSnapshot snapshot(
            int rank,
            List<String> types,
            List<String> sourceCategories,
            List<String> openingPeriods
    ) {
        return new GenerationCandidateSnapshot(
                rank,
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
                sourceCategories.contains("MUST_VISIT"),
                100.0,
                42.5
        );
    }
}
