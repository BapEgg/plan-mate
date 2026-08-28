package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.route.RouteTravelTimePort;
import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManualItineraryResponseServiceIdempotencyIntegrationTest {

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
    private RouteTravelTimePort routeTravelTimePort;

    private final List<Long> tripIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUpRouteProvider() {
        Mockito.when(routeTravelTimePort.findRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return Optional.of(new RouteTravelTimePort.RouteTravelTime(Duration.ofMinutes(30), 1_000));
                });
    }

    @AfterEach
    void tearDown() {
        tripIds.forEach(tripRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    @Test
    void sequentialSameReplayDoesNotWriteOrPublishAgain() {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft firstDraft = draft(fixture.generationId(), "place-1", "place-2", "place-3");

        manualItineraryResponseService.submit(fixture.userId(), fixture.tripId(), fixture.generationId(), firstDraft);

        assertThat(itineraryCount(fixture.generationId())).isEqualTo(1);
        assertThat(itemPlaceIds(fixture.generationId())).containsExactly("place-1", "place-3", "place-2");
        assertThat(completedEventCount(fixture.generationId())).isEqualTo(1);
        Instant completedUpdatedAt = generationRepository.findById(fixture.generationId()).orElseThrow().getUpdatedAt();

        AiItineraryDraft replay = new AiItineraryDraft(
                fixture.generationId().toString(),
                List.of(
                        day(2, List.of(item(1, " place-2 ", "10:00", 60))),
                        day(1, List.of(
                                item(2, "place-3", "13:00", 90),
                                item(1, "place-1", "09:00", 120)
                        ))
                )
        );

        manualItineraryResponseService.submit(fixture.userId(), fixture.tripId(), fixture.generationId(), replay);

        assertThat(itineraryCount(fixture.generationId())).isEqualTo(1);
        assertThat(dayCount(fixture.generationId())).isEqualTo(2);
        assertThat(itemCount(fixture.generationId())).isEqualTo(3);
        assertThat(completedEventCount(fixture.generationId())).isEqualTo(1);
        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getUpdatedAt())
                .isEqualTo(completedUpdatedAt);
    }

    @Test
    void validateAndSubmitCallRouteProviderOutsideTransaction() {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft draft = draft(fixture.generationId(), "place-1", "place-2", "place-3");

        assertThat(manualItineraryResponseService.validate(
                fixture.userId(), fixture.tripId(), fixture.generationId(), draft
        ).hasErrors()).isFalse();
        manualItineraryResponseService.submit(
                fixture.userId(), fixture.tripId(), fixture.generationId(), draft
        );

        Mockito.verify(routeTravelTimePort, Mockito.times(2))
                .findRoute(Mockito.any(), Mockito.any(), Mockito.eq(RouteTravelTimePort.TravelMode.WALK));
        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.COMPLETED);
    }

    @Test
    void routeProviderFailureKeepsGenerationReadyWithoutPersistenceOrEvent() {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft draft = draft(fixture.generationId(), "place-1", "place-2", "place-3");
        Mockito.when(routeTravelTimePort.findRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> manualItineraryResponseService.submit(
                fixture.userId(), fixture.tripId(), fixture.generationId(), draft
        )).isInstanceOf(ItineraryException.class)
                .satisfies(exception -> {
                    ItineraryException itineraryException = (ItineraryException) exception;
                    assertThat(itineraryException.code()).isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code());
                    assertThat(itineraryException.status().value()).isEqualTo(503);
                });

        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        assertThat(itineraryCount(fixture.generationId())).isZero();
        assertThat(completedEventCount(fixture.generationId())).isZero();
    }

    @Test
    void sequentialDifferentReplayConflictsWithoutChangingPersistedItinerary() {
        TripFixture fixture = createReadyGeneration();
        manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                fixture.generationId(),
                draft(fixture.generationId(), "place-1", "place-2", "place-3")
        );

        assertThatThrownBy(() -> manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                fixture.generationId(),
                draft(fixture.generationId(), "place-1", "place-4", "place-3")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("The generation has already been completed with a different itinerary draft.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT.code()));

        assertThat(itineraryCount(fixture.generationId())).isEqualTo(1);
        assertThat(itemPlaceIds(fixture.generationId())).containsExactly("place-1", "place-3", "place-2");
        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.COMPLETED);
        assertThat(completedEventCount(fixture.generationId())).isEqualTo(1);
    }

    @Test
    void concurrentSameDraftSubmitsOneItineraryAndPublishesOneCompletedEvent() throws Exception {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft first = draft(fixture.generationId(), "place-1", "place-2", "place-3");
        AiItineraryDraft second = new AiItineraryDraft(
                fixture.generationId().toString(),
                List.of(
                        day(2, List.of(item(1, "place-2", "10:00", 60))),
                        day(1, List.of(
                                item(2, "place-3", "13:00", 90),
                                item(1, " place-1 ", "09:00", 120)
                        ))
                )
        );

        List<Throwable> results = submitConcurrently(fixture, first, second);

        assertThat(results).allMatch(result -> result == null);
        assertThat(itineraryCount(fixture.generationId())).isEqualTo(1);
        assertThat(dayCount(fixture.generationId())).isEqualTo(2);
        assertThat(itemCount(fixture.generationId())).isEqualTo(3);
        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.COMPLETED);
        assertThat(completedEventCount(fixture.generationId())).isEqualTo(1);
    }

    @Test
    void concurrentDifferentDraftSubmitsOneItineraryAndRejectsOneReplay() throws Exception {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft first = draft(fixture.generationId(), "place-1", "place-2", "place-3");
        AiItineraryDraft second = draft(fixture.generationId(), "place-1", "place-4", "place-3");

        List<Throwable> results = submitConcurrently(fixture, first, second);

        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results)
                .filteredOn(result -> result instanceof ItineraryException exception
                        && exception.code().equals(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT.code()))
                .hasSize(1);
        assertThat(itineraryCount(fixture.generationId())).isEqualTo(1);
        assertThat(itemCount(fixture.generationId())).isEqualTo(3);
        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.COMPLETED);
        assertThat(completedEventCount(fixture.generationId())).isEqualTo(1);
    }

    @Test
    void validationFailureRollsBackAndKeepsGenerationReady() {
        TripFixture fixture = createReadyGeneration();
        AiItineraryDraft invalidDraft = new AiItineraryDraft(
                fixture.generationId().toString(),
                List.of(day(1, List.of(item(1, "place-1", "09:00", 120))))
        );

        assertThatThrownBy(() -> manualItineraryResponseService.submit(
                fixture.userId(),
                fixture.tripId(),
                fixture.generationId(),
                invalidDraft
        )).isInstanceOf(ItineraryException.class);

        assertThat(generationRepository.findById(fixture.generationId()).orElseThrow().getStatus())
                .isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        assertThat(itineraryCount(fixture.generationId())).isZero();
        assertThat(completedEventCount(fixture.generationId())).isZero();
    }

    private List<Throwable> submitConcurrently(
            TripFixture fixture,
            AiItineraryDraft first,
            AiItineraryDraft second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Throwable> firstResult = executor.submit(() -> submitAfterBarrier(fixture, first, barrier));
            Future<Throwable> secondResult = executor.submit(() -> submitAfterBarrier(fixture, second, barrier));
            List<Throwable> results = new ArrayList<>();
            results.add(firstResult.get(10, TimeUnit.SECONDS));
            results.add(secondResult.get(10, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable submitAfterBarrier(TripFixture fixture, AiItineraryDraft draft, CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            manualItineraryResponseService.submit(fixture.userId(), fixture.tripId(), fixture.generationId(), draft);
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private TripFixture createReadyGeneration() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "manual-idempotency-" + UUID.randomUUID() + "@example.com",
                "manual-idempotency@example.com",
                "manual-idempotency-user",
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
                List.of(
                        candidateSnapshot(1, "place-1"),
                        candidateSnapshot(2, "place-2"),
                        candidateSnapshot(3, "place-3"),
                        candidateSnapshot(4, "place-4")
                )
        );
        return new TripFixture(user.getId(), trip.getId(), generation.getId());
    }

    private TripCreateRequest tripCreateRequest() {
        return new TripCreateRequest(
                "Manual idempotency trip",
                "place-kyoto",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                new TripCreateRequest.CompanionRequest(2, CompanionType.FRIENDS, false, 0, null, false, 0),
                new TripCreateRequest.BudgetRequest(CurrencyCode.KRW, 1_000_000L, BudgetLevel.BALANCED, List.of(BudgetItem.FOOD)),
                new TripCreateRequest.PreferenceRequest(TravelPace.BALANCED, List.of(TripInterest.FOOD)),
                new TripCreateRequest.TransportationRequest(TransportMode.WALK, List.of()),
                new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, AccommodationArea.TRANSIT, null, null, null),
                new TripCreateRequest.SchedulePreferenceRequest(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                new TripCreateRequest.AdditionalRequest(List.of(), List.of(AvoidCondition.LONG_WALK), null)
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

    private AiItineraryDraft draft(Long generationId, String firstPlaceId, String secondDayPlaceId, String secondItemPlaceId) {
        return new AiItineraryDraft(
                generationId.toString(),
                List.of(
                        day(1, List.of(
                                item(1, firstPlaceId, "09:00", 120),
                                item(2, secondItemPlaceId, "13:00", 90)
                        )),
                        day(2, List.of(item(1, secondDayPlaceId, "10:00", 60)))
                )
        );
    }

    private ItineraryDraftDay day(int day, List<ItineraryDraftItem> items) {
        return new ItineraryDraftDay(day, items);
    }

    private ItineraryDraftItem item(int sequence, String placeId, String startTime, int durationMinutes) {
        return new ItineraryDraftItem(sequence, placeId, startTime, durationMinutes);
    }

    private long itineraryCount(Long generationId) {
        return count("SELECT COUNT(*) FROM itineraries WHERE generation_id = ?", generationId);
    }

    private long dayCount(Long generationId) {
        return count(
                """
                        SELECT COUNT(*)
                          FROM itinerary_days day
                          JOIN itineraries itinerary ON day.itinerary_id = itinerary.id
                         WHERE itinerary.generation_id = ?
                """,
                generationId
        );
    }

    private long itemCount(Long generationId) {
        return count(
                """
                        SELECT COUNT(*)
                          FROM itinerary_items item
                          JOIN itinerary_days day ON item.day_id = day.id
                          JOIN itineraries itinerary ON day.itinerary_id = itinerary.id
                         WHERE itinerary.generation_id = ?
                """,
                generationId
        );
    }

    private List<String> itemPlaceIds(Long generationId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT item.place_id
                          FROM itinerary_items item
                          JOIN itinerary_days day ON item.day_id = day.id
                          JOIN itineraries itinerary ON day.itinerary_id = itinerary.id
                         WHERE itinerary.generation_id = ?
                         ORDER BY day.day ASC, item.sequence ASC
                """,
                String.class,
                generationId
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
