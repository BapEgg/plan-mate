package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
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
import com.planmate.trip.repository.TripRepository;
import com.planmate.trip.service.TripCreationPersistenceService;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
class ItineraryGenerationCandidateWorkerIntegrationTest {

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
    private ItineraryGenerationWorkerService workerService;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private GenerationCandidateSnapshotRepository candidateRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CandidateRecommender candidateRecommender;

    private final List<Long> tripIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        tripIds.forEach(tripRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    @Test
    void workerCollectsRecommendedCandidatesWithoutWrappingExternalCallInItineraryTransaction() {
        TripFixture fixture = createTripFixture();
        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                fixture.userId(),
                fixture.tripId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );
        AtomicBoolean recommenderSawTransaction = new AtomicBoolean(true);
        given(candidateRecommender.recommend(any(CandidateRecommendationRequest.class)))
                .willAnswer(invocation -> {
                    recommenderSawTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    CandidateRecommendationRequest request = invocation.getArgument(0);
                    assertThat(request.destination().displayName()).isEqualTo("Kyoto");
                    assertThat(request.interests()).containsExactly(com.planmate.recommendation.api.Interest.FOOD);
                    assertThat(request.mustVisitPlaces()).hasSize(1);
                    return List.of(
                            recommendedCandidate(1, "candidate-1", false),
                            recommendedCandidate(2, "must-original", true)
                    );
                });

        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(
                generation.getId(),
                fixture.tripId(),
                fixture.userId()
        );
        workerService.process(message);
        workerService.process(message);
        entityManager.clear();

        ItineraryGenerationEntity result = generationRepository.findById(generation.getId()).orElseThrow();
        List<GenerationCandidateSnapshotEntity> candidates =
                candidateRepository.findByGeneration_IdOrderByRankAsc(generation.getId());
        ItineraryGenerationDetailResponse detail = persistenceService.getDetail(
                fixture.userId(),
                fixture.tripId(),
                generation.getId()
        );
        Optional<ItineraryGenerationDetailResponse> latest = persistenceService.getLatest(fixture.userId(), fixture.tripId());

        assertThat(recommenderSawTransaction).isFalse();
        verify(candidateRecommender, times(1)).recommend(any(CandidateRecommendationRequest.class));
        assertThat(result.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        assertThat(candidates).hasSize(2);
        assertThat(candidates)
                .extracting(GenerationCandidateSnapshotEntity::getPlaceId)
                .containsExactly("candidate-1", "must-original");
        assertThat(candidates.get(1).isForcedMustVisit()).isTrue();
        assertThat(detail.candidateCount()).isEqualTo(2);
        assertThat(latest).isPresent()
                .get()
                .extracting(ItineraryGenerationDetailResponse::candidateCount)
                .isEqualTo(2L);
    }

    private TripFixture createTripFixture() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "candidate-worker-" + UUID.randomUUID() + "@example.com",
                "candidate-worker@example.com",
                "candidate-worker-user",
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
        userIds.add(user.getId());
        tripIds.add(trip.getId());
        return new TripFixture(user.getId(), trip.getId());
    }

    private TripCreateRequest tripCreateRequest() {
        return new TripCreateRequest(
                "Candidate worker trip",
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

    private RecommendedPlaceCandidate recommendedCandidate(
            int rank,
            String placeId,
            boolean forcedMustVisit
    ) {
        return new RecommendedPlaceCandidate(
                rank,
                placeId,
                "Place " + placeId,
                "Address " + placeId,
                new CandidateRecommendationRequest.Location(35.0 + rank, 135.0 + rank),
                "museum",
                List.of("museum", "point_of_interest"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                forcedMustVisit ? List.of("MUST_VISIT") : List.of("CORE_VISIT"),
                forcedMustVisit,
                100.0 + rank,
                forcedMustVisit ? Double.MAX_VALUE : 42.5
        );
    }

    private record TripFixture(Long userId, Long tripId) {
    }
}
