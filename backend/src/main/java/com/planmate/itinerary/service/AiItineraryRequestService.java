package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.service.ItineraryGenerationPersistenceService.AiRequestContext;
import com.planmate.itinerary.api.RegenerationConstraintProvider;
import org.springframework.stereotype.Service;

@Service
public class AiItineraryRequestService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final AiItineraryRequestFactory requestFactory;
    private final ItineraryPromptService promptService;
    private final RegenerationConstraintProvider regenerationConstraintProvider;

    public AiItineraryRequestService(
            ItineraryGenerationPersistenceService persistenceService,
            AiItineraryRequestFactory requestFactory,
            ItineraryPromptService promptService,
            RegenerationConstraintProvider regenerationConstraintProvider
    ) {
        this.persistenceService = persistenceService;
        this.requestFactory = requestFactory;
        this.promptService = promptService;
        this.regenerationConstraintProvider = regenerationConstraintProvider;
    }

    public AiItineraryRequest getRequest(Long userId, Long tripId, Long generationId) {
        return createRequest(loadReadyContext(userId, tripId, generationId));
    }

    public String getPrompt(Long userId, Long tripId, Long generationId) {
        AiRequestContext context = loadReadyContext(userId, tripId, generationId);
        AiItineraryRequest request = createRequest(context);
        return promptService.createPrompt(context.promptVersion(), request);
    }

    private AiRequestContext loadReadyContext(Long userId, Long tripId, Long generationId) {
        AiRequestContext context = persistenceService.loadAiRequestContext(userId, tripId, generationId);
        if (context.status() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        return context;
    }

    private AiItineraryRequest createRequest(AiRequestContext context) {
        return requestFactory.create(
                context.promptVersion(),
                context.generationId(),
                context.inputSnapshot(),
                context.candidates(),
                regenerationConstraintProvider.findByGenerationId(context.generationId()).orElse(null)
        );
    }
}
