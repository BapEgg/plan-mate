package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.metrics.ItineraryGenerationPipelineMetrics;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private CandidateRecommendationRequestMapper candidateRecommendationRequestMapper;

    @Mock
    private CandidateRecommender candidateRecommender;

    @Mock
    private GenerationCandidateSnapshotMapper generationCandidateSnapshotMapper;

    @Mock
    private ItineraryGenerationPipelineMetrics pipelineMetrics;

    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationService(
                persistenceService,
                candidateRecommendationRequestMapper,
                candidateRecommender,
                generationCandidateSnapshotMapper,
                pipelineMetrics
        );
    }

    @Test
    void createOnlyCreatesGenerationRequestAndDoesNotCollectCandidates() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(persistenceService.createGenerationRequest(7L, 45L, ItineraryPromptService.CURRENT_PROMPT_VERSION))
                .willReturn(generation);

        ItineraryGenerationCreateResponse response = service.create(7L, 45L);

        assertThat(response.generationId()).isEqualTo("123");
        assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.CREATED);
        assertThat(response.candidateCount()).isZero();
        verify(persistenceService).createGenerationRequest(7L, 45L, ItineraryPromptService.CURRENT_PROMPT_VERSION);
        verify(persistenceService, never()).markCollecting(anyLong());
        verifyNoMoreInteractions(persistenceService);
    }

    @Test
    void collectCandidatesRecommendsAndStoresCandidateSnapshots() {
        GenerationInputSnapshot snapshot = snapshot(45L);
        CandidateRecommendationRequest request = request();
        RecommendedPlaceCandidate recommended = recommendedCandidate(1, "place-1");
        GenerationCandidateSnapshot candidateSnapshot = candidateSnapshot(1, "place-1");
        given(persistenceService.loadCollectionContext(45L, 123L))
                .willReturn(new ItineraryGenerationPersistenceService.GenerationCollectionContext(123L, snapshot));
        given(candidateRecommendationRequestMapper.map(snapshot)).willReturn(request);
        given(candidateRecommender.recommend(request)).willReturn(List.of(recommended));
        given(generationCandidateSnapshotMapper.map(recommended)).willReturn(candidateSnapshot);

        given(persistenceService.saveCandidatesAndMarkReady(123L, 7L, List.of(candidateSnapshot)))
                .willReturn(ItineraryGenerationPersistenceService.CandidateSaveResult.applied(1));

        assertThat(service.collectCandidates(45L, 123L, 7L)).isTrue();

        verify(persistenceService).loadCollectionContext(45L, 123L);
        verify(candidateRecommendationRequestMapper).map(snapshot);
        verify(candidateRecommender).recommend(request);
        verify(generationCandidateSnapshotMapper).map(recommended);
        verify(persistenceService).saveCandidatesAndMarkReady(123L, 7L, List.of(candidateSnapshot));
        verify(pipelineMetrics).recordCandidateCount(1);
    }

    @Test
    void staleCandidateSaveDoesNotRecordCandidateCount() {
        GenerationInputSnapshot snapshot = snapshot(45L);
        CandidateRecommendationRequest request = request();
        RecommendedPlaceCandidate recommended = recommendedCandidate(1, "place-1");
        GenerationCandidateSnapshot candidateSnapshot = candidateSnapshot(1, "place-1");
        given(persistenceService.loadCollectionContext(45L, 123L))
                .willReturn(new ItineraryGenerationPersistenceService.GenerationCollectionContext(123L, snapshot));
        given(candidateRecommendationRequestMapper.map(snapshot)).willReturn(request);
        given(candidateRecommender.recommend(request)).willReturn(List.of(recommended));
        given(generationCandidateSnapshotMapper.map(recommended)).willReturn(candidateSnapshot);
        given(persistenceService.saveCandidatesAndMarkReady(123L, 7L, List.of(candidateSnapshot)))
                .willReturn(ItineraryGenerationPersistenceService.CandidateSaveResult.stale());

        assertThat(service.collectCandidates(45L, 123L, 7L)).isFalse();

        verifyNoInteractions(pipelineMetrics);
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

    private GenerationInputSnapshot snapshot(Long tripId) {
        return new GenerationInputSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        null,
                        List.of("locality"),
                        "locality"
                ),
                new GenerationInputSnapshot.Companion(3, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }

    private CandidateRecommendationRequest request() {
        return new CandidateRecommendationRequest(
                new CandidateRecommendationRequest.Destination(
                        "Kyoto",
                        new CandidateRecommendationRequest.Location(35.0, 135.0),
                        null
                ),
                List.of(),
                null,
                List.of()
        );
    }

    private RecommendedPlaceCandidate recommendedCandidate(int rank, String placeId) {
        return new RecommendedPlaceCandidate(
                rank,
                placeId,
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                120.0,
                42.5
        );
    }

    private GenerationCandidateSnapshot candidateSnapshot(int rank, String placeId) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place",
                "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                120.0,
                42.5
        );
    }
}
