package com.planmate.itinerary.service;

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
        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                userId,
                tripId,
                ItineraryPromptService.PROMPT_VERSION
        );
        return new ItineraryGenerationCreateResponse(
                generation.getId().toString(),
                generation.getStatus(),
                0
        );
    }

    public void collectCandidates(Long userId, Long tripId, Long generationId) {
        GenerationCollectionContext context = persistenceService.loadCollectionContext(userId, tripId, generationId);
        List<CollectedPlaceCandidate> candidates = candidateCollectionService.collect(context.destination(), context.profile());
        persistenceService.saveCandidatesAndMarkReady(generationId, candidates);
    }

    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        return persistenceService.getDetail(userId, tripId, generationId);
    }

}
