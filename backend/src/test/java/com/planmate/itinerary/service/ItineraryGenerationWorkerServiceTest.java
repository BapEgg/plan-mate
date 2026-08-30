package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationWorkerServiceTest {

    @Mock
    private ItineraryGenerationPersistenceService persistenceService;

    @Mock
    private ItineraryGenerationService generationService;

    private ItineraryGenerationWorkerProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ItineraryGenerationWorkerService workerService;

    @BeforeEach
    void setUp() {
        properties = new ItineraryGenerationWorkerProperties();
        properties.setMaxAttempts(2);
        meterRegistry = new SimpleMeterRegistry();
        workerService = new ItineraryGenerationWorkerService(
                persistenceService,
                generationService,
                properties,
                new ItineraryGenerationWorkerMetrics(meterRegistry),
                new WorkerFailureClassifier()
        );
    }

    @Test
    void processCollectsCandidatesWhenGenerationIsCreated() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.claimCollection(45L, 123L, false, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(true, 1L));
        given(generationService.collectCandidates(45L, 123L, 1L)).willReturn(true);

        workerService.process(message, false);

        verify(persistenceService).claimCollection(45L, 123L, false, properties.getProcessingLease());
        verify(generationService).collectCandidates(45L, 123L, 1L);
        verify(persistenceService, never()).markFailed(anyLong(), anyLong(), anyString());
        assertProcessedCount("success", 1.0);
        assertDurationCount("success", 1L);
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.claim")
                        .tag("type", "initial")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void processIgnoresAlreadyHandledGeneration() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.claimCollection(45L, 123L, false, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(false, 0L));

        workerService.process(message);

        verify(persistenceService).claimCollection(45L, 123L, false, properties.getProcessingLease());
        verifyNoInteractions(generationService);
        assertProcessedCount("skipped", 1.0);
        assertDurationCount("skipped", 1L);
    }

    @Test
    void processRetriesAndMarksFailedWhenCandidateCollectionKeepsFailing() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        RuntimeException failure = new PlaceProviderUnavailableException();
        given(persistenceService.claimCollection(45L, 123L, false, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(true, 4L));
        doThrow(failure).when(generationService).collectCandidates(45L, 123L, 4L);
        given(persistenceService.markFailed(123L, 4L, "PLACE_PROVIDER_UNAVAILABLE")).willReturn(true);

        assertThatThrownBy(() -> workerService.process(message))
                .isSameAs(failure);

        verify(generationService, times(2)).collectCandidates(45L, 123L, 4L);
        verify(persistenceService).markFailed(123L, 4L, "PLACE_PROVIDER_UNAVAILABLE");
        assertProcessedCount("failed", 1.0);
        assertDurationCount("failed", 1L);
        assertRetryCount("retryable", "PLACE_PROVIDER_UNAVAILABLE", 1.0);
        assertFailureAttemptCount("retryable", "PLACE_PROVIDER_UNAVAILABLE", 2.0);
    }

    @Test
    void processRejectsInvalidMessage() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(null, 45L, null);

        assertThatThrownBy(() -> workerService.process(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itinerary generation message must include generationId and tripId");

        verifyNoInteractions(persistenceService, generationService);
        assertProcessedCount("failed", 1.0);
        assertDurationCount("failed", 1L);
    }

    @Test
    void nonRetryableFailureMarksFailedAfterFirstAttempt() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        RuntimeException failure = new IllegalStateException("invalid internal candidate data");
        given(persistenceService.claimCollection(45L, 123L, false, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(true, 4L));
        doThrow(failure).when(generationService).collectCandidates(45L, 123L, 4L);
        given(persistenceService.markFailed(123L, 4L, "WORKER_PROCESSING_FAILED")).willReturn(true);

        assertThatThrownBy(() -> workerService.process(message))
                .isSameAs(failure);

        verify(generationService).collectCandidates(45L, 123L, 4L);
        verify(persistenceService).markFailed(123L, 4L, "WORKER_PROCESSING_FAILED");
        assertThat(meterRegistry.find("planmate.itinerary.generation.worker.retry").counter()).isNull();
        assertFailureAttemptCount("non_retryable", "WORKER_PROCESSING_FAILED", 1.0);
    }

    @Test
    void staleClaimFailureIsIgnoredWithoutExceptionPropagation() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, null);
        RuntimeException failure = new PlaceProviderUnavailableException();
        given(persistenceService.claimCollection(45L, 123L, true, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(true, 2L));
        doThrow(failure).when(generationService).collectCandidates(45L, 123L, 2L);
        given(persistenceService.markFailed(123L, 2L, "PLACE_PROVIDER_UNAVAILABLE")).willReturn(false);

        workerService.process(message, true);

        verify(generationService, times(2)).collectCandidates(45L, 123L, 2L);
        assertProcessedCount("skipped", 1.0);
    }

    @Test
    void staleClaimSuccessReturnsNormallyWithoutReadyTransition() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, null);
        given(persistenceService.claimCollection(45L, 123L, true, properties.getProcessingLease()))
                .willReturn(new ItineraryGenerationPersistenceService.CollectionClaim(true, 2L));
        given(generationService.collectCandidates(45L, 123L, 2L)).willReturn(false);

        workerService.process(message, true);

        verify(persistenceService, never()).markFailed(anyLong(), anyLong(), anyString());
        assertProcessedCount("skipped", 1.0);
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.claim")
                        .tag("type", "recovery")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.fenced")
                        .tag("operation", "candidate_save")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    private void assertProcessedCount(String result, double count) {
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.processed")
                        .tag("result", result)
                        .counter()
                        .count())
                .isEqualTo(count);
    }

    private void assertDurationCount(String result, long count) {
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.duration")
                        .tag("result", result)
                        .timer()
                        .count())
                .isEqualTo(count);
    }

    private void assertRetryCount(String classification, String failureCode, double count) {
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.retry")
                        .tag("classification", classification)
                        .tag("failureCode", failureCode)
                        .counter()
                        .count())
                .isEqualTo(count);
    }

    private void assertFailureAttemptCount(String classification, String failureCode, double count) {
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.failure.attempt")
                        .tag("classification", classification)
                        .tag("failureCode", failureCode)
                        .counter()
                        .count())
                .isEqualTo(count);
    }
}
