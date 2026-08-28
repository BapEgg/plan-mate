package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GenerationCandidateSnapshotEntityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private GenerationCandidateSnapshotRepository candidateRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storesAndReadsFullSnapshotFields() {
        ItineraryGenerationEntity generation = createGeneration();
        candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                1,
                "place-1",
                List.of("museum", "point_of_interest"),
                "OPERATIONAL",
                List.of("CORE_VISIT"),
                false,
                120.0
        )));

        entityManager.clear();

        GenerationCandidateSnapshotEntity result = candidateRepository.findByGeneration_IdOrderByRankAsc(generation.getId()).get(0);

        assertThat(result.getPlaceId()).isEqualTo("place-1");
        assertThat(result.getName()).isEqualTo("Place place-1");
        assertThat(result.getAddress()).isEqualTo("Address place-1");
        assertThat(result.getLatitude()).isEqualTo(35.0);
        assertThat(result.getLongitude()).isEqualTo(135.0);
        assertThat(result.getPrimaryType()).isEqualTo("museum");
        assertThat(result.getTypes()).containsExactly("museum", "point_of_interest");
        assertThat(result.getBusinessStatus()).isEqualTo("OPERATIONAL");
        assertThat(result.getRating()).isEqualTo(4.5);
        assertThat(result.getUserRatingCount()).isEqualTo(100);
        assertThat(result.getSourceCategories()).containsExactly("CORE_VISIT");
        assertThat(result.getOpeningPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(result.isForcedMustVisit()).isFalse();
        assertThat(result.getDistanceMeters()).isEqualTo(120.0);
        assertThat(result.getScore()).isEqualTo(42.5);
        assertThat(result.getRank()).isEqualTo(1);
    }

    @Test
    void keepsNullableBusinessStatusAndDistanceMeters() {
        ItineraryGenerationEntity generation = createGeneration();
        candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                1,
                "place-1",
                List.of(),
                null,
                List.of("MUST_VISIT"),
                true,
                null
        )));

        entityManager.clear();

        GenerationCandidateSnapshotEntity result = candidateRepository.findByGeneration_IdOrderByRankAsc(generation.getId()).get(0);

        assertThat(result.getTypes()).isEmpty();
        assertThat(result.getBusinessStatus()).isNull();
        assertThat(result.isForcedMustVisit()).isTrue();
        assertThat(result.getDistanceMeters()).isNull();
    }

    @Test
    void rejectsDuplicatePlaceIdForSameGeneration() {
        ItineraryGenerationEntity generation = createGeneration();
        candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                1,
                "place-1",
                List.of("museum"),
                "OPERATIONAL",
                List.of("CORE_VISIT"),
                false,
                120.0
        )));

        assertThatThrownBy(() -> {
            candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                    2,
                    "place-1",
                    List.of("museum"),
                    "OPERATIONAL",
                    List.of("MEAL"),
                    false,
                    130.0
            )));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateRankForSameGeneration() {
        ItineraryGenerationEntity generation = createGeneration();
        candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                1,
                "place-1",
                List.of("museum"),
                "OPERATIONAL",
                List.of("CORE_VISIT"),
                false,
                120.0
        )));

        assertThatThrownBy(() -> {
            candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                    1,
                    "place-2",
                    List.of("restaurant"),
                    "OPERATIONAL",
                    List.of("MEAL"),
                    false,
                    130.0
            )));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesSnapshotsWhenGenerationIsDeleted() {
        ItineraryGenerationEntity generation = createGeneration();
        candidateRepository.saveAndFlush(GenerationCandidateSnapshotEntity.from(generation, snapshot(
                1,
                "place-1",
                List.of("museum"),
                "OPERATIONAL",
                List.of("CORE_VISIT"),
                false,
                120.0
        )));
        entityManager.flush();
        entityManager.clear();

        generationRepository.deleteById(generation.getId());
        generationRepository.flush();
        entityManager.clear();

        assertThat(candidateRepository.countByGeneration_Id(generation.getId())).isZero();
    }

    private ItineraryGenerationEntity createGeneration() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "candidate-snapshot-" + UUID.randomUUID() + "@example.com",
                "candidate-snapshot@example.com",
                "candidate-snapshot-user",
                true,
                NOW
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Candidate snapshot trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                34.8,
                134.8,
                35.2,
                135.2,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                user,
                NOW
        ));
        return generationRepository.saveAndFlush(ItineraryGenerationEntity.create(trip.getId(), "test", NOW));
    }

    private GenerationCandidateSnapshot snapshot(
            int rank,
            String placeId,
            List<String> types,
            String businessStatus,
            List<String> sourceCategories,
            boolean forcedMustVisit,
            Double distanceMeters
    ) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + placeId,
                "Address " + placeId,
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "museum",
                types,
                businessStatus,
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                sourceCategories,
                forcedMustVisit,
                distanceMeters,
                42.5
        );
    }
}
