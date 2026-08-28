package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationCandidateSnapshotMapperTest {

    private final GenerationCandidateSnapshotMapper mapper = new GenerationCandidateSnapshotMapper();

    @Test
    void mapsRecommendedCandidateToItineraryOwnedSnapshot() {
        RecommendedPlaceCandidate candidate = new RecommendedPlaceCandidate(
                2,
                "place-1",
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "museum",
                List.of("museum", "point_of_interest"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("MUST_VISIT"),
                true,
                120.0,
                42.5
        );

        GenerationCandidateSnapshot snapshot = mapper.map(candidate);

        assertThat(snapshot.rank()).isEqualTo(2);
        assertThat(snapshot.placeId()).isEqualTo("place-1");
        assertThat(snapshot.displayName()).isEqualTo("Place");
        assertThat(snapshot.formattedAddress()).isEqualTo("Address");
        assertThat(snapshot.location().latitude()).isEqualTo(35.0);
        assertThat(snapshot.location().longitude()).isEqualTo(135.0);
        assertThat(snapshot.primaryType()).isEqualTo("museum");
        assertThat(snapshot.types()).containsExactly("museum", "point_of_interest");
        assertThat(snapshot.businessStatus()).isEqualTo("OPERATIONAL");
        assertThat(snapshot.rating()).isEqualTo(4.5);
        assertThat(snapshot.userRatingCount()).isEqualTo(100);
        assertThat(snapshot.openingPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(snapshot.sourceCategories()).containsExactly("MUST_VISIT");
        assertThat(snapshot.forcedMustVisit()).isTrue();
        assertThat(snapshot.distanceMeters()).isEqualTo(120.0);
        assertThat(snapshot.score()).isEqualTo(42.5);
    }

    @Test
    void mapsEntityToItineraryOwnedSnapshot() {
        GenerationCandidateSnapshot original = new GenerationCandidateSnapshot(
                3,
                "place-3",
                "Place 3",
                "Address 3",
                new GenerationCandidateSnapshot.Location(35.3, 135.3),
                "park",
                List.of("park", "point_of_interest"),
                "OPERATIONAL",
                4.7,
                240,
                List.of("Tue 10:00-19:00"),
                List.of("NATURE", "MUST_VISIT"),
                true,
                340.5,
                98.75
        );
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                45L,
                ItineraryPromptService.CURRENT_PROMPT_VERSION,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        GenerationCandidateSnapshotEntity entity = GenerationCandidateSnapshotEntity.from(generation, original);

        GenerationCandidateSnapshot mapped = mapper.map(entity);

        assertThat(mapped.rank()).isEqualTo(3);
        assertThat(mapped.placeId()).isEqualTo("place-3");
        assertThat(mapped.displayName()).isEqualTo("Place 3");
        assertThat(mapped.formattedAddress()).isEqualTo("Address 3");
        assertThat(mapped.location().latitude()).isEqualTo(35.3);
        assertThat(mapped.location().longitude()).isEqualTo(135.3);
        assertThat(mapped.primaryType()).isEqualTo("park");
        assertThat(mapped.types()).containsExactly("park", "point_of_interest");
        assertThat(mapped.businessStatus()).isEqualTo("OPERATIONAL");
        assertThat(mapped.rating()).isEqualTo(4.7);
        assertThat(mapped.userRatingCount()).isEqualTo(240);
        assertThat(mapped.openingPeriods()).containsExactly("Tue 10:00-19:00");
        assertThat(mapped.sourceCategories()).containsExactly("NATURE", "MUST_VISIT");
        assertThat(mapped.forcedMustVisit()).isTrue();
        assertThat(mapped.distanceMeters()).isEqualTo(340.5);
        assertThat(mapped.score()).isEqualTo(98.75);
    }
}
