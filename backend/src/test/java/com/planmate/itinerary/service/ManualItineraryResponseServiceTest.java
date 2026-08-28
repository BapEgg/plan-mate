package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.metrics.AiItineraryValidationMetrics;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.route.RouteTravelTimePort;
import com.planmate.trip.api.TripAccessChecker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ManualItineraryResponseServiceTest {

    private final TripAccessChecker tripAccessChecker = Mockito.mock(TripAccessChecker.class);
    private final GenerationInputSnapshotStore generationInputSnapshotStore = Mockito.mock(GenerationInputSnapshotStore.class);
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore = Mockito.mock(GenerationCandidateSnapshotStore.class);
    private final RouteTravelTimePort routeTravelTimePort = Mockito.mock(RouteTravelTimePort.class);
    private final AiItineraryDraftValidationService aiItineraryDraftValidationService =
            new AiItineraryDraftValidationService(
                    new AiItineraryTimeValidationRule(),
                    new AiItineraryAvoidConditionValidationRule(),
                    new AiItineraryRepeatedPlaceValidationRule(),
                    new AiItineraryRouteValidationRule(routeTravelTimePort)
            );
    private final AiItineraryDraftNormalizer aiItineraryDraftNormalizer = new AiItineraryDraftNormalizer();
    private final ItineraryGenerationRepository generationRepository = Mockito.mock(ItineraryGenerationRepository.class);
    private final ItineraryRepository itineraryRepository = Mockito.mock(ItineraryRepository.class);
    private final ItineraryDayRepository itineraryDayRepository = Mockito.mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository itineraryItemRepository = Mockito.mock(ItineraryItemRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final AiItineraryValidationMetrics validationMetrics = Mockito.mock(AiItineraryValidationMetrics.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ManualItineraryResponsePersistenceService persistenceService =
            new ManualItineraryResponsePersistenceService(
                    aiItineraryDraftValidationService,
                    aiItineraryDraftNormalizer,
                    generationCandidateSnapshotStore,
                    generationRepository,
                    itineraryRepository,
                    itineraryDayRepository,
                    itineraryItemRepository,
                    clock,
                    eventPublisher
            );
    private final ManualItineraryResponseService service = new ManualItineraryResponseService(
            tripAccessChecker,
            generationInputSnapshotStore,
            generationCandidateSnapshotStore,
            aiItineraryDraftValidationService,
            aiItineraryDraftNormalizer,
            generationRepository,
            persistenceService,
            validationMetrics
    );

    private ItineraryGenerationEntity generation;

    @BeforeEach
    void setUp() {
        generation = ItineraryGenerationEntity.create(1L, ItineraryPromptService.CURRENT_PROMPT_VERSION, Instant.now(clock));
        generation.markCollecting(Instant.now(clock));
        generation.markReady(Instant.now(clock));
        ReflectionTestUtils.setField(generation, "id", 10L);

        given(generationRepository.findById(10L)).willReturn(Optional.of(generation));
        given(generationRepository.findWithLockById(10L)).willReturn(Optional.of(generation));
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.empty());
        given(generationInputSnapshotStore.getRequired(10L)).willReturn(snapshot());
        given(generationCandidateSnapshotStore.findAllByGenerationId(10L)).willReturn(List.of(
                candidate(1, "place-1", false),
                candidate(2, "place-2", false)
        ));
        given(routeTravelTimePort.findRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .willReturn(Optional.of(new RouteTravelTimePort.RouteTravelTime(Duration.ZERO, 0)));
    }

    @Test
    void savesPlaceIdOnlyDraftAndPublishesCompletedEvent() {
        given(itineraryRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryDayRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryItemRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(generationCandidateSnapshotStore.countByGenerationId(10L)).willReturn(2L);

        service.submit(99L, 1L, 10L, validDraft());

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        ArgumentCaptor<ItineraryItemEntity> itemCaptor = ArgumentCaptor.forClass(ItineraryItemEntity.class);
        verify(itineraryItemRepository, Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ItineraryItemEntity::getPlaceId)
                .containsExactly("place-1", "place-2");

        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(1L);
            assertThat(event.generationId()).isEqualTo(10L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
            assertThat(event.candidateCount()).isEqualTo(2);
            assertThat(event.failureReason()).isNull();
        });
        ArgumentCaptor<ItineraryEntity> itineraryCaptor = ArgumentCaptor.forClass(ItineraryEntity.class);
        verify(itineraryRepository).save(itineraryCaptor.capture());
        assertThat(itineraryCaptor.getValue().getTripId()).isEqualTo(generation.getTripId());
        assertThat(itineraryCaptor.getValue().getGeneration()).isSameAs(generation);
        verify(tripAccessChecker).checkAccessible(99L, 1L);
        verify(generationRepository).findWithLockById(10L);
    }

    @Test
    void rejectsDraftWhenGenerationBelongsToDifferentTrip() {
        assertThatThrownBy(() -> service.submit(99L, 2L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation not found.");
        verifyNoItinerarySaved();
    }

    @Test
    void returnsSuccessfullyForCompletedReplayWithSameCanonicalDraft() {
        generation.markCompleted(Instant.now(clock));
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.of(persistedItinerary(
                List.of(
                        persistedDay(2, persistedItem(1, "place-2", "09:00", 120)),
                        persistedDay(1, persistedItem(1, "place-1", "09:00", 120))
                )
        )));
        AiItineraryDraft replay = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, " place-1 ")),
                        day(2, item(1, "place-2"))
                )
        );

        service.submit(99L, 1L, 10L, replay);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        verify(itineraryRepository, never()).save(Mockito.any());
        verify(itineraryDayRepository, never()).save(Mockito.any());
        verify(itineraryItemRepository, never()).save(Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any());
        verifyNoInteractions(generationInputSnapshotStore, generationCandidateSnapshotStore);
        verifyNoInteractions(validationMetrics);
    }

    @Test
    void completedReplayDoesNotRunTimeValidation() {
        generation.markCompleted(Instant.now(clock));
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.of(persistedItinerary(
                List.of(
                        persistedDay(1, List.of(
                                persistedItem(1, "place-1", "09:00", 120),
                                persistedItem(2, "place-2", "10:00", 60)
                        )),
                        persistedDay(2, persistedItem(1, "place-2", "23:30", 90))
                )
        )));
        AiItineraryDraft replay = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(
                                item(1, "place-1", "09:00", 120),
                                item(2, "place-2", "10:00", 60)
                        )),
                        day(2, item(1, "place-2", "23:30", 90))
                )
        );

        service.submit(99L, 1L, 10L, replay);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        verify(itineraryRepository, never()).save(Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any());
        verifyNoInteractions(generationInputSnapshotStore, generationCandidateSnapshotStore);
    }

    @Test
    void rejectsCompletedReplayWithDifferentCanonicalDraft() {
        generation.markCompleted(Instant.now(clock));
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.of(persistedItinerary(
                List.of(
                        persistedDay(1, persistedItem(1, "place-1", "09:00", 120)),
                        persistedDay(2, persistedItem(1, "place-2", "09:00", 120))
                )
        )));
        AiItineraryDraft replay = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-3"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, replay))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("The generation has already been completed with a different itinerary draft.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT.code()));
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        verify(itineraryRepository, never()).save(Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void rejectsCompletedReplayWhenPersistedItineraryIsMissing() {
        generation.markCompleted(Instant.now(clock));
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation and persisted itinerary state are inconsistent.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT.code()));
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void rejectsReadyGenerationWhenPersistedItineraryAlreadyExists() {
        given(itineraryRepository.findByGeneration_Id(10L)).willReturn(Optional.of(persistedItinerary(
                List.of(
                        persistedDay(1, persistedItem(1, "place-1", "09:00", 120)),
                        persistedDay(2, persistedItem(1, "place-2", "09:00", 120))
                )
        )));

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation and persisted itinerary state are inconsistent.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT.code()));
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsDraftWhenGenerationIdDoesNotMatch() {
        AiItineraryDraft draft = new AiItineraryDraft("999", validDraft().days());

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(AiItineraryValidationException.class)
                .hasMessage("AI itinerary draft validation failed.")
                .satisfies(exception -> {
                    AiItineraryValidationException validationException = (AiItineraryValidationException) exception;
                    assertThat(validationException.code()).isEqualTo(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED.code());
                    assertThat(validationException.validationReport().errors())
                            .extracting(ValidationIssue::code)
                            .containsExactly(ValidationIssueCode.GENERATION_ID_MISMATCH);
                });
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsDraftWhenDayCountDoesNotMatchTripDuration() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(day(1, item(1, "place-1")))
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(AiItineraryValidationException.class)
                .hasMessage("AI itinerary draft validation failed.");
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsDraftWhenPlaceIdIsOutsideV2Whitelist() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-3"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(AiItineraryValidationException.class)
                .hasMessage("AI itinerary draft validation failed.")
                .satisfies(exception -> {
                    AiItineraryValidationException validationException = (AiItineraryValidationException) exception;
                    assertThat(validationException.code()).isEqualTo(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED.code());
                    assertThat(validationException.validationReport().errors())
                            .extracting(ValidationIssue::code)
                            .containsExactly(ValidationIssueCode.CANDIDATE_NOT_ALLOWED);
                });
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsV2DraftWhenCandidateSnapshotsAreMissing() {
        given(generationCandidateSnapshotStore.findAllByGenerationId(10L)).willReturn(List.of());

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation candidate snapshots not found.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_CANDIDATES_NOT_FOUND.code()));
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsDraftWhenStartTimeIsInvalid() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "9am", 120)),
                        day(2, item(1, "place-2"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(AiItineraryValidationException.class)
                .hasMessage("AI itinerary draft validation failed.");
        verifyNoItinerarySaved();
    }

    @Test
    void rejectsDraftWhenTimeValidationFailsBeforeSavingOrPublishing() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(
                                item(1, "place-1", "07:00", 120),
                                item(2, "place-2", "08:00", 60)
                        )),
                        day(2, item(1, "place-1", "23:30", 90))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(AiItineraryValidationException.class)
                .hasMessage("AI itinerary draft validation failed.")
                .satisfies(exception -> {
                    AiItineraryValidationException validationException = (AiItineraryValidationException) exception;
                    assertThat(validationException.validationReport().errors())
                            .extracting(ValidationIssue::code)
                            .containsExactly(
                                    ValidationIssueCode.OUTSIDE_DAILY_WINDOW,
                                    ValidationIssueCode.ITEM_TIME_OVERLAP,
                                    ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY
                            );
                });
        verifyNoItinerarySaved();
        verify(eventPublisher, never()).publishEvent(Mockito.any());
        verify(routeTravelTimePort, never()).findRoute(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void validateReturnsReportWithoutSavingOrPublishing() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "outside"))
                )
        );

        AiItineraryValidationReport report = service.validate(99L, 1L, 10L, draft);

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.CANDIDATE_NOT_ALLOWED);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verifyNoItinerarySaved();
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void validateReturnsTimeErrorsWithoutSavingOrPublishing() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(
                                item(1, "place-1", "09:00", 120),
                                item(2, "place-2", "10:00", 60)
                        )),
                        day(2, item(1, "place-2", "09:00", 120))
                )
        );

        AiItineraryValidationReport report = service.validate(99L, 1L, 10L, draft);

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ITEM_TIME_OVERLAP);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verifyNoItinerarySaved();
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void validateReturnsRouteConstraintViolationWithoutSavingOrPublishing() {
        given(routeTravelTimePort.findRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .willReturn(Optional.of(new RouteTravelTimePort.RouteTravelTime(Duration.ofMinutes(61), 5_000)));

        AiItineraryValidationReport report = service.validate(99L, 1L, 10L, routeDraft());

        assertThat(report.errors()).extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.INSUFFICIENT_TRAVEL_TIME);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verifyNoItinerarySaved();
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void submitRejectsRouteConstraintViolationBeforeSavingOrPublishing() {
        given(routeTravelTimePort.findRoute(Mockito.any(), Mockito.any(), Mockito.any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, routeDraft()))
                .isInstanceOf(AiItineraryValidationException.class)
                .satisfies(exception -> assertThat(
                        ((AiItineraryValidationException) exception).validationReport().errors()
                ).extracting(ValidationIssue::code)
                        .containsExactly(ValidationIssueCode.ADJACENT_ROUTE_NOT_FOUND));

        verifyNoItinerarySaved();
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void validateRejectsGenerationThatIsNotReady() {
        generation.markCompleted(Instant.now(clock));

        assertThatThrownBy(() -> service.validate(99L, 1L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation is not ready for planning.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_NOT_READY.code()));

        verifyNoInteractions(generationInputSnapshotStore, generationCandidateSnapshotStore);
        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }

    @Test
    void warningAndUnverifiedOnlyReportDoesNotBlockPersistence() {
        AiItineraryDraftValidationService validationService = Mockito.mock(AiItineraryDraftValidationService.class);
        ManualItineraryResponsePersistenceService persistenceServiceWithValidationReport =
                new ManualItineraryResponsePersistenceService(
                        validationService,
                        aiItineraryDraftNormalizer,
                        generationCandidateSnapshotStore,
                        generationRepository,
                        itineraryRepository,
                        itineraryDayRepository,
                        itineraryItemRepository,
                        clock,
                        eventPublisher
                );
        ManualItineraryResponseService serviceWithValidationReport = new ManualItineraryResponseService(
                tripAccessChecker,
                generationInputSnapshotStore,
                generationCandidateSnapshotStore,
                validationService,
                aiItineraryDraftNormalizer,
                generationRepository,
                persistenceServiceWithValidationReport,
                validationMetrics
        );
        given(validationService.validate(
                Mockito.eq(10L),
                Mockito.eq(generation.getPromptVersion()),
                Mockito.any(GenerationInputSnapshot.class),
                Mockito.anyList(),
                Mockito.eq(validDraft())
        )).willReturn(new AiItineraryValidationReport(
                List.of(),
                List.of(ValidationIssue.of(ValidationIssueCode.REQUIRED_PLACE_MISSING, "days", null, null, "warning-place")),
                List.of(ValidationIssue.of(ValidationIssueCode.CANDIDATE_NOT_ALLOWED, "days", null, null, "unverified-place"))
        ));
        given(itineraryRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryDayRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryItemRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(generationCandidateSnapshotStore.countByGenerationId(10L)).willReturn(2L);

        serviceWithValidationReport.submit(99L, 1L, 10L, validDraft());

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        verify(itineraryRepository).save(Mockito.any());
        verify(eventPublisher).publishEvent(Mockito.any(ItineraryGenerationStatusChangedEvent.class));
    }

    @Test
    void rejectsLegacyGenerationWithoutStoredInputSnapshot() {
        given(generationInputSnapshotStore.getRequired(10L))
                .willThrow(new ItineraryException(com.planmate.itinerary.exception.ItineraryErrorCode.GENERATION_INPUT_NOT_FOUND));

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
        verifyNoItinerarySaved();
    }

    private AiItineraryDraft validDraft() {
        return new AiItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-2"))
                )
        );
    }

    private AiItineraryDraft routeDraft() {
        return new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(
                                item(1, "place-1", "09:00", 60),
                                item(2, "place-2", "11:00", 60)
                        )),
                        day(2, item(1, "place-1"))
                )
        );
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftDay day(int day, List<ItineraryDraftItem> items) {
        return new ItineraryDraftDay(day, items);
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 120);
    }

    private ItineraryDraftItem item(int sequence, String placeId, String startTime, int durationMinutes) {
        return new ItineraryDraftItem(sequence, placeId, startTime, durationMinutes);
    }

    private ItineraryEntity persistedItinerary(List<ItineraryDayEntity> days) {
        ItineraryEntity itinerary = ItineraryEntity.create(generation, Instant.now(clock));
        ReflectionTestUtils.setField(itinerary, "days", days);
        return itinerary;
    }

    private ItineraryDayEntity persistedDay(int day, ItineraryItemEntity item) {
        return persistedDay(day, List.of(item));
    }

    private ItineraryDayEntity persistedDay(int day, List<ItineraryItemEntity> items) {
        ItineraryDayEntity entity = ItineraryDayEntity.create(
                ItineraryEntity.create(generation, Instant.now(clock)),
                day,
                LocalDate.of(2026, 10, 8).plusDays(day)
        );
        ReflectionTestUtils.setField(entity, "items", items);
        items.forEach(item -> ReflectionTestUtils.setField(item, "day", entity));
        return entity;
    }

    private ItineraryItemEntity persistedItem(int sequence, String placeId, String startTime, int durationMinutes) {
        return ItineraryItemEntity.create(
                ItineraryDayEntity.create(ItineraryEntity.create(generation, Instant.now(clock)), 1, LocalDate.of(2026, 10, 9)),
                sequence,
                placeId,
                LocalTime.parse(startTime),
                durationMinutes,
                ItineraryItemCreatedSource.AI_DRAFT
        );
    }

    private GenerationInputSnapshot snapshot() {
        return new GenerationInputSnapshot(
                1L,
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
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
                new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Transportation("WALK", List.of()),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(mustVisitPlace("place-1"), mustVisitPlace("place-2")),
                List.of(),
                null
        );
    }

    private GenerationInputSnapshot.MustVisitPlace mustVisitPlace(String placeId) {
        return new GenerationInputSnapshot.MustVisitPlace(
                placeId,
                "Place",
                "Address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId, boolean forcedMustVisit) {
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
                forcedMustVisit,
                100.0,
                42.5
        );
    }

    private void verifyNoItinerarySaved() {
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verify(itineraryRepository, never()).save(Mockito.any());
        verify(itineraryDayRepository, never()).save(Mockito.any());
        verify(itineraryItemRepository, never()).save(Mockito.any());
    }
}
