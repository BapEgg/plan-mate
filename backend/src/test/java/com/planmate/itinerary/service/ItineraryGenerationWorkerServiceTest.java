package com.planmate.itinerary.service;

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
    private ItineraryGenerationWorkerService workerService;

    @BeforeEach
    void setUp() {
        properties = new ItineraryGenerationWorkerProperties();
        properties.setMaxAttempts(2);
        workerService = new ItineraryGenerationWorkerService(persistenceService, generationService, properties);
    }

    @Test
    void processCollectsCandidatesWhenGenerationIsCreated() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.markCollectingIfCreated(7L, 45L, 123L)).willReturn(true);

        workerService.process(message);

        verify(persistenceService).markCollectingIfCreated(7L, 45L, 123L);
        verify(generationService).collectCandidates(7L, 45L, 123L);
        verify(persistenceService, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void processIgnoresAlreadyHandledGeneration() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(123L, 45L, 7L);
        given(persistenceService.markCollectingIfCreated(7L, 45L, 123L)).willReturn(false);

        workerService.process(message);

        verify(persistenceService).markCollectingIfCreated(7L, 45L, 123L);
        verifyNoInteractions(generationService);
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
    }

    @Test
    void processRejectsInvalidMessage() {
        ItineraryGenerationRequestedMessage message = new ItineraryGenerationRequestedMessage(null, 45L, 7L);

        assertThatThrownBy(() -> workerService.process(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itinerary generation message must include generationId, tripId, and userId");

        verifyNoInteractions(persistenceService, generationService);
    }
}
