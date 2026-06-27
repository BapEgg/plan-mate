package com.planmate.itinerary.service;

import com.planmate.common.exception.PlanMateException;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.service.ItineraryGenerationPersistenceService.GenerationCollectionContext;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.recommendation.service.PlaceCandidateCollectionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final PlaceCandidateCollectionService candidateCollectionService;

    public ItineraryGenerationService(
            ItineraryGenerationPersistenceService persistenceService,
            PlaceCandidateCollectionService candidateCollectionService
    ) {
        this.persistenceService = persistenceService;
        this.candidateCollectionService = candidateCollectionService;
    }

    public ItineraryGenerationCreateResponse create(Long userId, Long tripId) {
        ItineraryGenerationEntity generation = persistenceService.createGeneration(
                userId,
                tripId,
                ItineraryPromptService.PROMPT_VERSION
        );
        try {
            persistenceService.markCollecting(generation.getId());
            GenerationCollectionContext context = persistenceService.loadCollectionContext(userId, tripId, generation.getId());
            List<CollectedPlaceCandidate> candidates = candidateCollectionService.collect(context.destination(), context.profile());
            persistenceService.saveCandidatesAndMarkReady(generation.getId(), candidates);
            ItineraryGenerationDetailResponse detail = persistenceService.getDetail(userId, tripId, generation.getId());
            return new ItineraryGenerationCreateResponse(
                    detail.generationId(),
                    detail.status(),
                    detail.candidateCount()
            );
        } catch (RuntimeException exception) {
            persistenceService.markFailed(generation.getId(), safeFailureReason(exception));
            throw exception;
        }
    }

    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        return persistenceService.getDetail(userId, tripId, generationId);
    }

    private String safeFailureReason(RuntimeException exception) {
        if (exception instanceof PlanMateException planMateException) {
            return planMateException.code();
        }
        return exception.getClass().getSimpleName();
    }
}
