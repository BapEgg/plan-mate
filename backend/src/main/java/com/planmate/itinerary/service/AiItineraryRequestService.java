package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.service.ItineraryGenerationPersistenceService.AiRequestContext;
import org.springframework.stereotype.Service;

@Service
public class AiItineraryRequestService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final AiItineraryRequestFactory requestFactory;
    private final ItineraryPromptService promptService;

    public AiItineraryRequestService(
            ItineraryGenerationPersistenceService persistenceService,
            AiItineraryRequestFactory requestFactory,
            ItineraryPromptService promptService
    ) {
        this.persistenceService = persistenceService;
        this.requestFactory = requestFactory;
        this.promptService = promptService;
    }

    public AiItineraryRequest getRequest(Long userId, Long tripId, Long generationId) {
        AiRequestContext context = persistenceService.loadAiRequestContext(userId, tripId, generationId);
        if (context.generation().getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        return requestFactory.create(context.generation(), context.profile());
    }

    public String getPrompt(Long userId, Long tripId, Long generationId) {
        return promptService.createPrompt(getRequest(userId, tripId, generationId));
    }
}
