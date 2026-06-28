package com.planmate.itinerary.service;

import com.planmate.common.exception.PlanMateException;
import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationWorkerService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final ItineraryGenerationService generationService;
    private final ItineraryGenerationWorkerProperties properties;

    public ItineraryGenerationWorkerService(
            ItineraryGenerationPersistenceService persistenceService,
            ItineraryGenerationService generationService,
            ItineraryGenerationWorkerProperties properties
    ) {
        this.persistenceService = persistenceService;
        this.generationService = generationService;
        this.properties = properties;
    }

    public void process(ItineraryGenerationRequestedMessage message) {
        validate(message);
        boolean shouldProcess = persistenceService.markCollectingIfCreated(
                message.userId(),
                message.tripId(),
                message.generationId()
        );
        if (!shouldProcess) {
            return;
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                generationService.collectCandidates(message.userId(), message.tripId(), message.generationId());
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }

        persistenceService.markFailed(message.generationId(), safeFailureReason(lastFailure));
        throw lastFailure;
    }

    private void validate(ItineraryGenerationRequestedMessage message) {
        if (message.generationId() == null || message.tripId() == null || message.userId() == null) {
            throw new IllegalArgumentException("itinerary generation message must include generationId, tripId, and userId");
        }
    }

    private String safeFailureReason(RuntimeException exception) {
        if (exception instanceof PlanMateException planMateException) {
            return planMateException.code();
        }
        return exception.getClass().getSimpleName();
    }
}
