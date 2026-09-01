package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ItineraryEntityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void enforcesSingleItineraryPerGeneration() {
        ItineraryGenerationEntity generation = createGeneration();
        itineraryRepository.saveAndFlush(ItineraryEntity.create(generation, NOW, 1));

        assertThatThrownBy(() -> itineraryRepository.saveAndFlush(ItineraryEntity.create(generation, NOW.plusSeconds(1), 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOneItineraryForEachGeneration() {
        ItineraryGenerationEntity first = createGeneration();
        ItineraryGenerationEntity second = generationRepository.saveAndFlush(
                ItineraryGenerationEntity.create(first.getTripId(), "test", NOW)
        );

        itineraryRepository.saveAndFlush(ItineraryEntity.create(first, NOW, 1));
        itineraryRepository.saveAndFlush(ItineraryEntity.create(second, NOW.plusSeconds(1), 2));

        assertThat(itineraryRepository.findByGeneration_Id(first.getId())).isPresent();
        assertThat(itineraryRepository.findByGeneration_Id(second.getId())).isPresent();
    }

    @Test
    void exposesUniqueConstraintAndDropsLegacyGenerationIndex() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM pg_constraint
                         WHERE conname = 'itineraries_generation_unique'
                """,
                Integer.class
        );
        String indexRegclass = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.itineraries_generation_id_idx')",
                String.class
        );

        assertThat(constraintCount).isEqualTo(1);
        assertThat(indexRegclass).isNull();
    }

    @Test
    void exposesGenerationStatusConstraintAndAllowsOnlyCurrentStatuses() {
        Long tripId = createGeneration().getTripId();

        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM pg_constraint
                         WHERE conname = 'itinerary_generations_status_check'
                """,
                Integer.class
        );
        assertThat(constraintCount).isEqualTo(1);

        for (ItineraryGenerationStatus status : ItineraryGenerationStatus.values()) {
            assertThatCode(() -> insertGeneration(tripId, status.name()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsPlanningGenerationStatus() {
        Long tripId = createGeneration().getTripId();

        assertThatThrownBy(() -> insertGeneration(tripId, "PLANNING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsValidatingGenerationStatus() {
        Long tripId = createGeneration().getTripId();

        assertThatThrownBy(() -> insertGeneration(tripId, "VALIDATING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesItineraryWhenGenerationIsDeleted() {
        ItineraryGenerationEntity generation = createGeneration();
        itineraryRepository.saveAndFlush(ItineraryEntity.create(generation, NOW, 1));

        entityManager.clear();
        generationRepository.deleteById(generation.getId());
        generationRepository.flush();
        entityManager.clear();

        assertThat(itineraryRepository.findByGeneration_Id(generation.getId())).isEmpty();
    }

    private ItineraryGenerationEntity createGeneration() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "itinerary-entity-" + UUID.randomUUID() + "@example.com",
                "itinerary-entity@example.com",
                "itinerary-entity-user",
                true,
                NOW
        ));
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Itinerary entity trip",
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

    private void insertGeneration(Long tripId, String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO itinerary_generations (
                            trip_id,
                            status,
                            prompt_version,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, 'test', ?, ?)
                """,
                tripId,
                status,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW)
        );
    }
}
