package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationRepository generationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TripAccessChecker tripAccessChecker;

    @Mock
    private TripPlanningSnapshotReader tripPlanningSnapshotReader;

    @Mock
    private GenerationInputSnapshotMapper generationInputSnapshotMapper;

    @Mock
    private GenerationInputSnapshotStore generationInputSnapshotStore;

    @Mock
    private GenerationCandidateSnapshotStore generationCandidateSnapshotStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ItineraryGenerationPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationPersistenceService(
                generationRepository,
                outboxEventRepository,
                tripAccessChecker,
                tripPlanningSnapshotReader,
                generationInputSnapshotMapper,
                generationInputSnapshotStore,
                generationCandidateSnapshotStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void createGenerationRequestStoresGenerationAndOutboxEventInOneServiceCall() {
        TripPlanningSnapshot snapshot = snapshot(45L);
        GenerationInputSnapshot inputSnapshot = inputSnapshot(45L);
        given(tripPlanningSnapshotReader.findByTripId(45L)).willReturn(Optional.of(snapshot));
        given(generationInputSnapshotMapper.map(snapshot)).willReturn(inputSnapshot);
        given(generationRepository.save(any(ItineraryGenerationEntity.class)))
                .willAnswer(invocation -> {
                    ItineraryGenerationEntity generation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(generation, "id", 123L);
                    return generation;
                });

        ItineraryGenerationEntity generation = service.createGenerationRequest(
                7L,
                45L,
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );

        assertThat(generation.getId()).isEqualTo(123L);
        assertThat(generation.getTripId()).isEqualTo(45L);
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getId()).isNotNull();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ITINERARY_GENERATION");
        assertThat(outboxEvent.getAggregateId()).isEqualTo("123");
        assertThat(outboxEvent.getEventType()).isEqualTo("ITINERARY_GENERATION_REQUESTED");
        assertThat(outboxEvent.getPayload())
                .containsEntry("generationId", 123L)
                .containsEntry("tripId", 45L)
                .containsEntry("userId", 7L);
        assertThat(outboxEvent.getCreatedAt()).isEqualTo(NOW);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
        verify(generationInputSnapshotStore).save(123L, inputSnapshot, NOW);
    }

    @Test
    void createGenerationRequestRejectsMissingPlanningProfileBeforeSaving() {
        given(tripPlanningSnapshotReader.findByTripId(45L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGenerationRequest(7L, 45L, ItineraryPromptService.CURRENT_PROMPT_VERSION))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Trip planning profile not found.");

        verify(tripAccessChecker).checkAccessible(7L, 45L);
        verifyNoInteractions(generationRepository, generationInputSnapshotStore, outboxEventRepository);
    }

    @Test
    void createGenerationRequestRejectsUnresolvedDestinationBeforeSaving() {
        given(tripPlanningSnapshotReader.findByTripId(45L)).willReturn(Optional.of(snapshotWithoutDestinationLocation(45L)));

        assertThatThrownBy(() -> service.createGenerationRequest(7L, 45L, ItineraryPromptService.CURRENT_PROMPT_VERSION))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Trip destination has not been resolved.");

        verify(tripAccessChecker).checkAccessible(7L, 45L);
        verifyNoInteractions(generationRepository, generationInputSnapshotMapper, generationInputSnapshotStore, outboxEventRepository);
    }

    @Test
    void markCollectingIfCreatedMarksCreatedGenerationAndReturnsTrue() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isTrue();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void markCollectingIfCreatedReturnsFalseForAlreadyProcessedGeneration() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        generation.markReady(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isFalse();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verifyNoInteractions(eventPublisher);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void markFailedPublishesStatusChangedEvent() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(generationCandidateSnapshotStore.countByGenerationId(123L)).willReturn(2L);

        service.markFailed(123L, "GOOGLE_PLACES_UNAVAILABLE");

        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(45L);
            assertThat(event.generationId()).isEqualTo(123L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.FAILED);
            assertThat(event.candidateCount()).isEqualTo(2);
            assertThat(event.failureReason()).isEqualTo("GOOGLE_PLACES_UNAVAILABLE");
        });
    }

    @Test
    void saveCandidatesAndMarkReadyStoresCandidatesAndPublishesActualCount() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        List<GenerationCandidateSnapshot> candidates = List.of(
                candidate(1, "place-1"),
                candidate(2, "place-2")
        );
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));
        given(generationCandidateSnapshotStore.replaceAll(generation, candidates)).willReturn(2);

        int result = service.saveCandidatesAndMarkReady(123L, candidates);

        assertThat(result).isEqualTo(2);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verify(generationCandidateSnapshotStore).replaceAll(generation, candidates);
        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
            assertThat(event.candidateCount()).isEqualTo(2);
        });
    }

    @Test
    void saveCandidatesAndMarkReadyReturnsExistingCountWhenAlreadyReady() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        generation.markReady(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));
        given(generationCandidateSnapshotStore.countByGenerationId(123L)).willReturn(2L);

        int result = service.saveCandidatesAndMarkReady(123L, List.of(candidate(1, "place-1")));

        assertThat(result).isEqualTo(2);
        verify(generationCandidateSnapshotStore, never()).replaceAll(any(), anyList());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveCandidatesAndMarkReadyRejectsEmptyCandidates() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.saveCandidatesAndMarkReady(123L, List.of()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("No usable recommendation candidates were found.");

        verify(generationCandidateSnapshotStore, never()).replaceAll(any(), anyList());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveCandidatesAndMarkReadyRejectsDuplicatePlaceId() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.saveCandidatesAndMarkReady(
                123L,
                List.of(candidate(1, "place-1"), candidate(2, "place-1"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeId");

        verify(generationCandidateSnapshotStore, never()).replaceAll(any(), anyList());
    }

    @Test
    void saveCandidatesAndMarkReadyRejectsDuplicateRank() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.saveCandidatesAndMarkReady(
                123L,
                List.of(candidate(1, "place-1"), candidate(1, "place-2"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank");

        verify(generationCandidateSnapshotStore, never()).replaceAll(any(), anyList());
    }

    @Test
    void saveCandidatesAndMarkReadyRejectsNonContinuousRank() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.saveCandidatesAndMarkReady(
                123L,
                List.of(candidate(1, "place-1"), candidate(3, "place-2"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("continuous");

        verify(generationCandidateSnapshotStore, never()).replaceAll(any(), anyList());
    }

    @Test
    void getLatestReturnsLatestGenerationAfterTripAccessCheck() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        generation.markReady(NOW);
        given(generationRepository.findFirstByTripIdOrderByCreatedAtDesc(45L)).willReturn(Optional.of(generation));
        given(generationCandidateSnapshotStore.countByGenerationId(123L)).willReturn(4L);

        Optional<com.planmate.itinerary.dto.ItineraryGenerationDetailResponse> result = service.getLatest(7L, 45L);

        assertThat(result).isPresent()
                .get()
                .satisfies(response -> {
                    assertThat(response.generationId()).isEqualTo("123");
                    assertThat(response.tripId()).isEqualTo("45");
                    assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
                    assertThat(response.candidateCount()).isEqualTo(4);
                });
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void getLatestReturnsEmptyWhenTripHasNoGeneration() {
        given(generationRepository.findFirstByTripIdOrderByCreatedAtDesc(45L)).willReturn(Optional.empty());

        Optional<com.planmate.itinerary.dto.ItineraryGenerationDetailResponse> result = service.getLatest(7L, 45L);

        assertThat(result).isEmpty();
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void loadCollectionContextReturnsStoredInputSnapshot() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        GenerationInputSnapshot snapshot = inputSnapshot(45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(generationInputSnapshotStore.getRequired(123L)).willReturn(snapshot);

        ItineraryGenerationPersistenceService.GenerationCollectionContext result = service.loadCollectionContext(7L, 45L, 123L);

        assertThat(result.generationId()).isEqualTo(123L);
        assertThat(result.snapshot()).isSameAs(snapshot);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void loadAiRequestContextReturnsPromptVersionStoredInputSnapshotAndCandidates() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        GenerationInputSnapshot snapshot = inputSnapshot(45L);
        List<GenerationCandidateSnapshot> candidates = List.of(
                candidate(1, "place-1"),
                candidate(2, "place-2")
        );
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(generationInputSnapshotStore.getRequired(123L)).willReturn(snapshot);
        given(generationCandidateSnapshotStore.findAllByGenerationId(123L)).willReturn(candidates);

        ItineraryGenerationPersistenceService.AiRequestContext result = service.loadAiRequestContext(7L, 45L, 123L);

        assertThat(result.generationId()).isEqualTo(123L);
        assertThat(result.tripId()).isEqualTo(45L);
        assertThat(result.status()).isEqualTo(ItineraryGenerationStatus.CREATED);
        assertThat(result.promptVersion()).isEqualTo(ItineraryPromptService.CURRENT_PROMPT_VERSION);
        assertThat(result.inputSnapshot()).isSameAs(snapshot);
        assertThat(result.candidates()).containsExactlyElementsOf(candidates);
        assertThatThrownBy(() -> result.candidates().add(candidate(3, "place-3")))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void loadAiRequestContextThrowsWhenInputSnapshotIsMissing() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(generationInputSnapshotStore.getRequired(123L))
                .willThrow(new ItineraryException(com.planmate.itinerary.exception.ItineraryErrorCode.GENERATION_INPUT_NOT_FOUND));

        assertThatThrownBy(() -> service.loadAiRequestContext(7L, 45L, 123L))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
    }

    @Test
    void loadAiRequestContextRejectsGenerationFromDifferentTrip() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.loadAiRequestContext(7L, 46L, 123L))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation not found.");
    }

    @Test
    void staleClaimCannotStoreCandidatesOrMarkReady() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        long firstClaim = generation.claimCollection(NOW, Duration.ofMinutes(15), false);
        generation.claimCollection(NOW.plusSeconds(1), Duration.ofMinutes(15), true);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        ItineraryGenerationPersistenceService.CandidateSaveResult result =
                service.saveCandidatesAndMarkReady(123L, firstClaim, List.of(candidate(1, "place-1")));

        assertThat(result.applied()).isFalse();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        verifyNoInteractions(generationCandidateSnapshotStore, eventPublisher);
    }

    @Test
    void staleClaimCannotMarkGenerationFailed() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        long firstClaim = generation.claimCollection(NOW, Duration.ofMinutes(15), false);
        generation.claimCollection(NOW.plusSeconds(1), Duration.ofMinutes(15), true);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean failed = service.markFailed(123L, firstClaim, "provider failure");

        assertThat(failed).isFalse();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        verifyNoInteractions(generationCandidateSnapshotStore, eventPublisher);
    }

    @Test
    void currentClaimStoresSingleCandidateSetAndMarksReady() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        long claimVersion = generation.claimCollection(NOW, Duration.ofMinutes(15), false);
        GenerationCandidateSnapshot candidate = candidate(1, "place-1");
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));
        given(generationCandidateSnapshotStore.replaceAll(generation, List.of(candidate))).willReturn(1);

        ItineraryGenerationPersistenceService.CandidateSaveResult result =
                service.saveCandidatesAndMarkReady(123L, claimVersion, List.of(candidate));

        assertThat(result).isEqualTo(new ItineraryGenerationPersistenceService.CandidateSaveResult(true, 1));
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verify(generationCandidateSnapshotStore).replaceAll(generation, List.of(candidate));
        verify(eventPublisher).publishEvent(any(ItineraryGenerationStatusChangedEvent.class));
    }

    private ItineraryGenerationEntity generation(Long generationId, Long tripId) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                tripId,
                ItineraryPromptService.CURRENT_PROMPT_VERSION,
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }

    private TripPlanningSnapshot snapshot(Long tripId) {
        return new TripPlanningSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new TripPlanningSnapshot.Destination("place-kyoto", "Kyoto", "Kyoto, Japan", 35.0, 135.0, null, List.of("locality"), "locality"),
                new TripPlanningSnapshot.Companion(3, "FRIENDS", false, 0, null, false, 0),
                new TripPlanningSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Preference("BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                java.time.LocalTime.of(8, 0),
                java.time.LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }

    private TripPlanningSnapshot snapshotWithoutDestinationLocation(Long tripId) {
        TripPlanningSnapshot snapshot = snapshot(tripId);
        return new TripPlanningSnapshot(
                snapshot.tripId(),
                snapshot.startDate(),
                snapshot.endDate(),
                new TripPlanningSnapshot.Destination("place-kyoto", "Kyoto", "Kyoto, Japan", null, null, null, List.of("locality"), "locality"),
                snapshot.companion(),
                snapshot.budget(),
                snapshot.preference(),
                snapshot.transportation(),
                snapshot.accommodation(),
                snapshot.dailyStartTime(),
                snapshot.dailyEndTime(),
                snapshot.mustVisitPlaces(),
                snapshot.avoidConditions(),
                snapshot.freeRequest()
        );
    }

    private GenerationInputSnapshot inputSnapshot(Long tripId) {
        return new GenerationInputSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination("place-kyoto", "Kyoto", "Kyoto, Japan", 35.0, 135.0, null, List.of("locality"), "locality"),
                new GenerationInputSnapshot.Companion(3, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                java.time.LocalTime.of(8, 0),
                java.time.LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + rank,
                "Address",
                new GenerationCandidateSnapshot.Location(35.0 + rank, 135.0 + rank),
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                100.0 + rank,
                42.5 - rank
        );
    }
}
