package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.metrics.ItineraryGenerationPipelineMetrics;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final CandidateRecommendationRequestMapper candidateRecommendationRequestMapper;
    private final CandidateRecommender candidateRecommender;
    private final GenerationCandidateSnapshotMapper generationCandidateSnapshotMapper;
    private final ItineraryGenerationPipelineMetrics pipelineMetrics;

    public ItineraryGenerationService(
            ItineraryGenerationPersistenceService persistenceService,
            CandidateRecommendationRequestMapper candidateRecommendationRequestMapper,
            CandidateRecommender candidateRecommender,
            GenerationCandidateSnapshotMapper generationCandidateSnapshotMapper,
            ItineraryGenerationPipelineMetrics pipelineMetrics
    ) {
        this.persistenceService = persistenceService;
        this.candidateRecommendationRequestMapper = candidateRecommendationRequestMapper;
        this.candidateRecommender = candidateRecommender;
        this.generationCandidateSnapshotMapper = generationCandidateSnapshotMapper;
        this.pipelineMetrics = pipelineMetrics;
    }

    public ItineraryGenerationCreateResponse create(Long userId, Long tripId) {
        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                userId,
                tripId,
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        );
        return new ItineraryGenerationCreateResponse(
                generation.getId().toString(),
                generation.getStatus(),
                0
        );
    }

    public boolean collectCandidates(Long tripId, Long generationId, long claimVersion) {
        ItineraryGenerationPersistenceService.GenerationCollectionContext context =
                persistenceService.loadCollectionContext(tripId, generationId);
        CandidateRecommendationRequest request = candidateRecommendationRequestMapper.map(context.snapshot());
        List<RecommendedPlaceCandidate> recommendedCandidates = candidateRecommender.recommend(request);
        List<GenerationCandidateSnapshot> snapshots = recommendedCandidates.stream()
                .map(generationCandidateSnapshotMapper::map)
                .toList();
        ItineraryGenerationPersistenceService.CandidateSaveResult saveResult =
                persistenceService.saveCandidatesAndMarkReady(generationId, claimVersion, snapshots);
        if (saveResult.applied()) {
            pipelineMetrics.recordCandidateCount(saveResult.candidateCount());
        }
        return saveResult.applied();
    }

    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        return persistenceService.getDetail(userId, tripId, generationId);
    }

    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        return persistenceService.getLatest(userId, tripId);
    }

}
