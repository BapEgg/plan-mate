package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.recommendation.service.PlaceCandidateCollectionService;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationPersistenceService persistenceService;

    @Mock
    private PlaceCandidateCollectionService candidateCollectionService;

    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationService(persistenceService, candidateCollectionService);
    }

    @Test
    void createOnlyCreatesGenerationRequestAndDoesNotCollectCandidates() {
        ItineraryGenerationEntity generation = generation(123L, trip(45L));
        given(persistenceService.createGenerationRequest(7L, 45L, ItineraryPromptService.PROMPT_VERSION))
                .willReturn(generation);

        ItineraryGenerationCreateResponse response = service.create(7L, 45L);

        assertThat(response.generationId()).isEqualTo("123");
        assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.CREATED);
        assertThat(response.candidateCount()).isZero();
        verify(persistenceService).createGenerationRequest(7L, 45L, ItineraryPromptService.PROMPT_VERSION);
        verify(persistenceService, never()).markCollecting(anyLong());
        verifyNoMoreInteractions(persistenceService);
        verifyNoInteractions(candidateCollectionService);
    }

    @Test
    void collectCandidatesRunsMovedCandidateCollectionFlow() {
        ResolvedDestination destination = new ResolvedDestination(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0, 135.0),
                null,
                List.of("locality"),
                "locality"
        );
        TripPlanningProfileEntity profile = org.mockito.Mockito.mock(TripPlanningProfileEntity.class);
        CollectedPlaceCandidate candidate = org.mockito.Mockito.mock(CollectedPlaceCandidate.class);
        List<CollectedPlaceCandidate> candidates = List.of(candidate);
        given(persistenceService.loadCollectionContext(7L, 45L, 123L))
                .willReturn(new ItineraryGenerationPersistenceService.GenerationCollectionContext(123L, destination, profile));
        given(candidateCollectionService.collect(destination, profile)).willReturn(candidates);

        service.collectCandidates(7L, 45L, 123L);

        verify(persistenceService).markCollecting(123L);
        verify(persistenceService).loadCollectionContext(7L, 45L, 123L);
        verify(candidateCollectionService).collect(destination, profile);
        verify(persistenceService).saveCandidatesAndMarkReady(123L, candidates);
    }

    private ItineraryGenerationEntity generation(Long generationId, TripEntity trip) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                trip,
                ItineraryPromptService.PROMPT_VERSION,
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }

    private TripEntity trip(Long tripId) {
        TripEntity trip = TripEntity.create(
                "Kyoto trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                null,
                null,
                null,
                null,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                null,
                NOW
        );
        ReflectionTestUtils.setField(trip, "id", tripId);
        return trip;
    }
}
