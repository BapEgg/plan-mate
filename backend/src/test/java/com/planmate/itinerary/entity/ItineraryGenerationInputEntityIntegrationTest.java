package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.repository.ItineraryGenerationInputRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ItineraryGenerationInputEntityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private ItineraryGenerationInputRepository inputRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesAndReadsJsonbPayloadRoundTrip() {
        ItineraryGenerationEntity generation = createGeneration();
        GenerationInputSnapshot snapshot = snapshot(generation.getTripId());

        inputRepository.saveAndFlush(ItineraryGenerationInputEntity.create(
                generation.getId(),
                1,
                snapshot,
                NOW
        ));

        entityManager.clear();

        ItineraryGenerationInputEntity result = inputRepository.findById(generation.getId()).orElseThrow();

        assertThat(result.getSnapshotVersion()).isEqualTo(1);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getPayload().startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.getPayload().dailyStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.getPayload().destination().viewport().highLongitude()).isEqualTo(135.2);
        assertThat(result.getPayload().preference().interests()).containsExactly("FOOD", "SIGHTSEEING");
        assertThat(result.getPayload().mustVisitPlaces()).hasSize(1);
        assertThat(result.getPayload().mustVisitPlaces().get(0).types()).containsExactly("tourist_attraction");
    }

    @Test
    void rejectsDuplicateGenerationId() {
        ItineraryGenerationEntity generation = createGeneration();
        inputRepository.saveAndFlush(ItineraryGenerationInputEntity.create(generation.getId(), 1, snapshot(generation.getTripId()), NOW));

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO itinerary_generation_inputs(generation_id, snapshot_version, payload, created_at)
                        VALUES (?, 1, CAST('{}' AS jsonb), ?)
                """,
                generation.getId(),
                Timestamp.from(NOW)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInputForMissingGeneration() {
        assertThatThrownBy(() -> {
            inputRepository.saveAndFlush(ItineraryGenerationInputEntity.create(9_999_999L, 1, snapshot(45L), NOW));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesInputWhenGenerationIsDeleted() {
        ItineraryGenerationEntity generation = createGeneration();
        inputRepository.saveAndFlush(ItineraryGenerationInputEntity.create(generation.getId(), 1, snapshot(generation.getTripId()), NOW));

        generationRepository.delete(generation);
        generationRepository.flush();
        entityManager.clear();

        assertThat(inputRepository.findById(generation.getId())).isEmpty();
    }

    private ItineraryGenerationEntity createGeneration() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "snapshot-" + UUID.randomUUID() + "@example.com",
                "snapshot@example.com",
                "snapshot-user",
                true,
                NOW
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Snapshot trip",
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

    private GenerationInputSnapshot snapshot(Long tripId) {
        return new GenerationInputSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        new GenerationInputSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                        List.of("locality"),
                        "locality"
                ),
                new GenerationInputSnapshot.Companion(3, "FRIENDS", false, 0, null, true, 1),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD", "LODGING")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD", "SIGHTSEEING")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation(
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
                ),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(new GenerationInputSnapshot.MustVisitPlace(
                        "must-1",
                        "Kiyomizu",
                        "Kiyomizu address",
                        35.0,
                        135.0,
                        List.of("tourist_attraction"),
                        "tourist_attraction"
                )),
                List.of("LONG_WALK"),
                "Keep lunch flexible."
        );
    }
}
