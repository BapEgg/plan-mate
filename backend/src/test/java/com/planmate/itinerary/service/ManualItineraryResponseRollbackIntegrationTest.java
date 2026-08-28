package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import com.planmate.trip.service.TripCreationPersistenceService;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManualItineraryResponseRollbackIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripCreationPersistenceService tripCreationPersistenceService;

    @Autowired
    private ItineraryGenerationPersistenceService persistenceService;

    @Autowired
    private ManualItineraryResponseService manualItineraryResponseService;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private ItineraryDayRepository itineraryDayRepository;

    private final List<Long> tripIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        tripIds.forEach(tripRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    @Test
    void rollsBackItineraryAndGenerationStateWhenDaySaveFails() {
        TripFixture fixture = createReadyGeneration();
        willThrow(new IllegalStateException("day save failed"))
                .given(itineraryDayRepository)
                .save(any(ItineraryDayEntity.class));

        assertThatThrownBy(() -> manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                fixture.generationId(),
                draft(fixture.generationId())
        )).isInstanceOf(IllegalStateException.class);

        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        assertThat(count("SELECT COUNT(*) FROM itineraries WHERE generation_id = ?", fixture.generationId()))
                .isZero();
        assertThat(completedEventCount(fixture.generationId())).isZero();
    }

    private TripFixture createReadyGeneration() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "manual-rollback-" + UUID.randomUUID() + "@example.com",
                "manual-rollback@example.com",
                "manual-rollback-user",
                true,
                NOW
        ));
        TripEntity trip = tripCreationPersistenceService.create(
                user.getId(),
                tripCreateRequest(),
                destination(),
                null,
                List.of(),
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0))
        );
        userIds.add(user.getId());
        tripIds.add(trip.getId());

        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                user.getId(),
                trip.getId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );
        persistenceService.markCollectingIfCreated(user.getId(), trip.getId(), generation.getId());
        persistenceService.saveCandidatesAndMarkReady(
                generation.getId(),
                List.of(candidateSnapshot(1, "place-1"), candidateSnapshot(2, "place-2"))
        );
        return new TripFixture(user.getId(), trip.getId(), generation.getId());
    }

    private TripCreateRequest tripCreateRequest() {
        return new TripCreateRequest(
                "Manual rollback trip",
                "place-kyoto",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                new TripCreateRequest.CompanionRequest(2, CompanionType.FRIENDS, false, 0, null, false, 0),
                new TripCreateRequest.BudgetRequest(CurrencyCode.KRW, 1_000_000L, BudgetLevel.BALANCED, List.of(BudgetItem.FOOD)),
                new TripCreateRequest.PreferenceRequest(TravelPace.BALANCED, List.of(TripInterest.FOOD)),
                new TripCreateRequest.TransportationRequest(TransportMode.PUBLIC_TRANSIT, List.of(TransportMode.WALK)),
                new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, AccommodationArea.TRANSIT, null, null, null),
                new TripCreateRequest.SchedulePreferenceRequest(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                new TripCreateRequest.AdditionalRequest(List.of(), List.of(), null)
        );
    }

    private com.planmate.place.api.ResolvedPlace destination() {
        return new com.planmate.place.api.ResolvedPlace(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new com.planmate.place.api.GeoPoint(35.0, 135.0),
                new com.planmate.place.api.GeoViewport(
                        new com.planmate.place.api.GeoPoint(34.8, 134.8),
                        new com.planmate.place.api.GeoPoint(35.2, 135.2)
                ),
                List.of("locality"),
                "locality"
        );
    }

    private GenerationCandidateSnapshot candidateSnapshot(int rank, String placeId) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + rank,
                "Address " + rank,
                new GenerationCandidateSnapshot.Location(35.0 + rank, 135.0 + rank),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                100.0,
                42.5
        );
    }

    private AiItineraryDraft draft(Long generationId) {
        return new AiItineraryDraft(
                generationId.toString(),
                List.of(
                        new ItineraryDraftDay(1, List.of(new ItineraryDraftItem(1, "place-1", "09:00", 120))),
                        new ItineraryDraftDay(2, List.of(new ItineraryDraftItem(1, "place-2", "10:00", 60)))
                )
        );
    }

    private long count(String sql, Long generationId) {
        return jdbcTemplate.queryForObject(sql, Long.class, generationId);
    }

    private long completedEventCount(Long generationId) {
        return applicationEvents.stream(ItineraryGenerationStatusChangedEvent.class)
                .filter(event -> event.generationId().equals(generationId))
                .filter(event -> event.status() == ItineraryGenerationStatus.COMPLETED)
                .count();
    }

    private record TripFixture(
            Long userId,
            Long tripId,
            Long generationId
    ) {
    }
}
