package com.planmate.itinerary.service;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationWorkerService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final ItineraryGenerationService generationService;
    private final ItineraryGenerationWorkerProperties properties;
    private final ItineraryGenerationWorkerMetrics metrics;
    private final WorkerFailureClassifier failureClassifier;

    public ItineraryGenerationWorkerService(
            ItineraryGenerationPersistenceService persistenceService,
            ItineraryGenerationService generationService,
            ItineraryGenerationWorkerProperties properties,
            ItineraryGenerationWorkerMetrics metrics,
            WorkerFailureClassifier failureClassifier
    ) {
        this.persistenceService = persistenceService;
        this.generationService = generationService;
        this.properties = properties;
        this.metrics = metrics;
        this.failureClassifier = failureClassifier;
    }

    public void process(ItineraryGenerationRequestedMessage message, boolean redelivered) {
        Timer.Sample sample = metrics.start();
        String result = ItineraryGenerationWorkerMetrics.RESULT_FAILED;
        try {
            validate(message);
            ItineraryGenerationPersistenceService.CollectionClaim claim = persistenceService.claimCollection(
                    message.tripId(),
                    message.generationId(),
                    redelivered,
                    properties.getProcessingLease()
            );
            if (!claim.claimed()) {
                result = ItineraryGenerationWorkerMetrics.RESULT_SKIPPED;
                return;
            }

            result = collectCandidatesWithRetry(message, claim.claimVersion())
                    ? ItineraryGenerationWorkerMetrics.RESULT_SUCCESS
                    : ItineraryGenerationWorkerMetrics.RESULT_SKIPPED;
        } finally {
            metrics.recordProcessed(result, sample);
        }
    }

    public void process(ItineraryGenerationRequestedMessage message) {
        process(message, false);
    }

    private boolean collectCandidatesWithRetry(ItineraryGenerationRequestedMessage message, long claimVersion) {
        RuntimeException lastFailure = null;
        WorkerFailureClassifier.WorkerFailure classifiedFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                return generationService.collectCandidates(message.tripId(), message.generationId(), claimVersion);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                classifiedFailure = failureClassifier.classify(exception);
                if (!classifiedFailure.retryable()) {
                    break;
                }
                if (attempt < properties.getMaxAttempts()) {
                    metrics.recordRetry();
                }
            }
        }

        boolean failed = persistenceService.markFailed(
                message.generationId(),
                claimVersion,
                classifiedFailure.reason()
        );
        if (failed) {
            throw lastFailure;
        }
        return false;
    }

    private void validate(ItineraryGenerationRequestedMessage message) {
        if (message.generationId() == null || message.tripId() == null) {
            throw new IllegalArgumentException("itinerary generation message must include generationId and tripId");
        }
    }

}
