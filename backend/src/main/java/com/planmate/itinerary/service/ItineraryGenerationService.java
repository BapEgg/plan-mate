package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationService {

    private final ItineraryGenerationPersistenceService persistenceService;

    public ItineraryGenerationService(ItineraryGenerationPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
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
        persistenceService.loadCollectionContext(userId, tripId, generationId);
        persistenceService.markReadyForPlanning(generationId);
    }

    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        return persistenceService.getDetail(userId, tripId, generationId);
    }

    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        return persistenceService.getLatest(userId, tripId);
    }

}
