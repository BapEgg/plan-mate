package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryGenerationInputRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.trip.service.TripCreationPersistenceService;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ItineraryGenerationInputSnapshotFlowIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripPlanningProfileRepository tripPlanningProfileRepository;

    @Autowired
    private TripCreationPersistenceService tripCreationPersistenceService;

    @Autowired
    private ItineraryGenerationPersistenceService persistenceService;

    @Autowired
    private AiItineraryRequestService aiItineraryRequestService;

    @Autowired
    private ManualItineraryResponseService manualItineraryResponseService;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private ItineraryGenerationInputRepository inputRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createGenerationRequestStoresGenerationInputSnapshotAndOutbox() {
        TripFixture fixture = createTripFixture();

        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                fixture.userId(),
                fixture.tripId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );

        GenerationInputSnapshot input = inputRepository.findById(generation.getId()).orElseThrow().getPayload();
        assertThat(generation.getPromptVersion()).isEqualTo(ItineraryPromptService.CURRENT_PROMPT_VERSION);
        assertThat(input.tripId()).isEqualTo(fixture.tripId());
        assertThat(input.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(input.endDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(input.destination().displayName()).isEqualTo("Kyoto");
        assertThat(input.mustVisitPlaces())
                .extracting(GenerationInputSnapshot.MustVisitPlace::placeId)
                .containsExactly("must-original");

        OutboxEventEntity outboxEvent = outboxEventRepository.findAll()
                .stream()
                .filter(event -> event.getAggregateId().equals(generation.getId().toString()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> payload = outboxEvent.getPayload();
        assertThat(payload).containsOnlyKeys("generationId", "tripId", "userId");
        assertThat(payload.get("generationId").toString()).isEqualTo(generation.getId().toString());
        assertThat(payload.get("tripId").toString()).isEqualTo(fixture.tripId().toString());
        assertThat(payload.get("userId").toString()).isEqualTo(fixture.userId().toString());
    }

    @Test
    void storedSnapshotIsUsedAfterTripAndProfileAreChanged() {
        TripFixture fixture = createTripFixture();
        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                fixture.userId(),
                fixture.tripId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );
        persistenceService.markCollectingIfCreated(fixture.userId(), fixture.tripId(), generation.getId());
        persistenceService.saveCandidatesAndMarkReady(
                generation.getId(),
                List.of(
                        candidateSnapshot(1, "must-original", true),
                        candidateSnapshot(2, "place-2", false)
                )
        );
        entityManager.flush();
        entityManager.clear();

        changeTripAndProfile(fixture.tripId());
        entityManager.flush();
        entityManager.clear();

        ItineraryGenerationPersistenceService.GenerationCollectionContext collectionContext =
                persistenceService.loadCollectionContext(fixture.userId(), fixture.tripId(), generation.getId());
        assertThat(collectionContext.snapshot().endDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(collectionContext.snapshot().mustVisitPlaces())
                .extracting(GenerationInputSnapshot.MustVisitPlace::placeId)
                .containsExactly("must-original");

        AiItineraryRequest request = aiItineraryRequestService.getRequest(fixture.userId(), fixture.tripId(), generation.getId());
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(request.mustVisitPlaces())
                .extracting(AiItineraryRequest.MustVisitPlace::placeId)
                .containsExactly("must-original");
        assertThat(request.dailyWindow()).isEqualTo(new AiItineraryRequest.DailyWindow(
                LocalTime.of(8, 0),
                LocalTime.of(20, 0)
        ));
        assertThat(request.candidates())
                .extracting(AiItineraryRequest.Candidate::placeId)
                .containsExactly("must-original", "place-2");
        assertThat(request.rules()).isEmpty();

        manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                generation.getId(),
                new AiItineraryDraft(
                        generation.getId().toString(),
                        List.of(
                                new ItineraryDraftDay(1, List.of(new ItineraryDraftItem(1, "must-original", "09:00", 60))),
                                new ItineraryDraftDay(2, List.of(new ItineraryDraftItem(1, "place-2", "09:00", 60)))
                        )
                )
        );
        assertThat(itineraryRepository.findFirstByTripIdOrderByCreatedAtDesc(fixture.tripId())).isPresent();
    }

    @Test
    void legacyGenerationWithoutInputSnapshotKeepsDetailQueriesButFailsProcessingContexts() {
        TripFixture fixture = createTripFixture();
        ItineraryGenerationEntity generation = generationRepository.save(ItineraryGenerationEntity.create(
                fixture.tripId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION,
                NOW
        ));
        generation.markCollecting(NOW);
        generation.markReady(NOW);
        entityManager.flush();

        ItineraryGenerationDetailResponse detail = persistenceService.getDetail(
                fixture.userId(),
                fixture.tripId(),
                generation.getId()
        );
        Optional<ItineraryGenerationDetailResponse> latest = persistenceService.getLatest(fixture.userId(), fixture.tripId());

        assertThat(detail.generationId()).isEqualTo(generation.getId().toString());
        assertThat(latest).isPresent();
        assertThatThrownBy(() -> persistenceService.loadCollectionContext(fixture.userId(), fixture.tripId(), generation.getId()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
        assertThatThrownBy(() -> aiItineraryRequestService.getRequest(fixture.userId(), fixture.tripId(), generation.getId()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
        assertThatThrownBy(() -> manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                generation.getId(),
                new AiItineraryDraft(generation.getId().toString(), List.of(new ItineraryDraftDay(1, List.of(new ItineraryDraftItem(1, "must-original", "09:00", 60)))))
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
    }

    private TripFixture createTripFixture() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "generation-input-" + UUID.randomUUID() + "@example.com",
                "generation-input@example.com",
                "generation-input-user",
                true,
                NOW
        ));
        TripEntity trip = tripCreationPersistenceService.create(
                user.getId(),
                tripCreateRequest(),
                destination(),
                null,
                List.of(mustVisitPlace("must-original")),
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0))
        );
        return new TripFixture(user.getId(), trip.getId());
    }

    private void changeTripAndProfile(Long tripId) {
        TripEntity trip = tripRepository.findById(tripId).orElseThrow();
        ReflectionTestUtils.setField(trip, "endDate", LocalDate.of(2026, 4, 4));
        ReflectionTestUtils.setField(trip, "updatedAt", NOW.plusSeconds(60));

        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(tripId).orElseThrow();
        ReflectionTestUtils.setField(profile, "mustVisitPlaces", List.of(mustVisitPlace("must-changed")));
        ReflectionTestUtils.setField(profile, "updatedAt", NOW.plusSeconds(60));
    }

    private TripCreateRequest tripCreateRequest() {
        return new TripCreateRequest(
                "Snapshot trip",
                "place-kyoto",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                new TripCreateRequest.CompanionRequest(2, CompanionType.FRIENDS, false, 0, null, false, 0),
                new TripCreateRequest.BudgetRequest(CurrencyCode.KRW, 1_000_000L, BudgetLevel.BALANCED, List.of(BudgetItem.FOOD)),
                new TripCreateRequest.PreferenceRequest(TravelPace.BALANCED, List.of(TripInterest.FOOD)),
                new TripCreateRequest.TransportationRequest(TransportMode.PUBLIC_TRANSIT, List.of(TransportMode.WALK)),
                new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, AccommodationArea.TRANSIT, null, null, null),
                new TripCreateRequest.SchedulePreferenceRequest(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                new TripCreateRequest.AdditionalRequest(List.of("must-original"), List.of(AvoidCondition.LONG_WALK), "Keep lunch flexible.")
        );
    }

    private ResolvedPlace destination() {
        return new ResolvedPlace(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0, 135.0),
                new GeoViewport(new GeoPoint(34.8, 134.8), new GeoPoint(35.2, 135.2)),
                List.of("locality"),
                "locality"
        );
    }

    private MustVisitPlaceSnapshot mustVisitPlace(String placeId) {
        return new MustVisitPlaceSnapshot(
                placeId,
                "Must " + placeId,
                "Must address",
                35.01,
                135.01,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }

    private GenerationCandidateSnapshot candidateSnapshot(int rank, String placeId, boolean forcedMustVisit) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Must " + placeId,
                "Must address",
                new GenerationCandidateSnapshot.Location(35.01, 135.01),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.5,
                100,
                List.of(),
                List.of("MUST_VISIT"),
                forcedMustVisit,
                0.0,
                Double.MAX_VALUE
        );
    }

    private record TripFixture(
            Long userId,
            Long tripId
    ) {
    }
}
