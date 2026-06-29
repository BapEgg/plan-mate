package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
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
                new ItineraryGenerationWorkerMetrics(meterRegistry)
        );
    }

    @Test
    void processCollectsCandidatesWhenGenerationIsCreated() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.markCollectingIfCreated(7L, 45L, 123L)).willReturn(true);

        workerService.process(message);

        verify(persistenceService).markCollectingIfCreated(7L, 45L, 123L);
        verify(generationService).collectCandidates(7L, 45L, 123L);
        verify(persistenceService, never()).markFailed(anyLong(), anyString());
        assertProcessedCount("success", 1.0);
        assertDurationCount("success", 1L);
    }

    @Test
    void processIgnoresAlreadyHandledGeneration() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.markCollectingIfCreated(7L, 45L, 123L)).willReturn(false);

        workerService.process(message);

        verify(persistenceService).markCollectingIfCreated(7L, 45L, 123L);
        verifyNoInteractions(generationService);
        assertProcessedCount("skipped", 1.0);
        assertDurationCount("skipped", 1L);
    }

    @Test
    void processRetriesAndMarksFailedWhenCandidateCollectionKeepsFailing() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        RuntimeException failure = new IllegalStateException("google places unavailable");
        given(persistenceService.markCollectingIfCreated(7L, 45L, 123L)).willReturn(true);
        doThrow(failure).when(generationService).collectCandidates(7L, 45L, 123L);

        assertThatThrownBy(() -> workerService.process(message))
                .isSameAs(failure);

        verify(generationService, times(2)).collectCandidates(7L, 45L, 123L);
        verify(persistenceService).markFailed(123L, "IllegalStateException");
        assertProcessedCount("failed", 1.0);
        assertDurationCount("failed", 1L);
        assertRetryCount(1.0);
    }

    @Test
    void processRejectsInvalidMessage() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(null, 45L, 7L);

        assertThatThrownBy(() -> workerService.process(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itinerary generation message must include generationId, tripId, and userId");

        verifyNoInteractions(persistenceService, generationService);
        assertProcessedCount("failed", 1.0);
        assertDurationCount("failed", 1L);
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

    private void assertRetryCount(double count) {
        assertThat(meterRegistry.get("planmate.itinerary.generation.worker.retry")
                        .counter()
                        .count())
                .isEqualTo(count);
    }
}
