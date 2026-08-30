package com.planmate.itinerary.service;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationWorkerService.class);
    private static final String RETRYABLE = "retryable";
    private static final String NON_RETRYABLE = "non_retryable";

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

            metrics.recordClaim(claim.claimVersion());
            log.info(
                    "Itinerary generation collection claim acquired: generationId={}, tripId={}, "
                            + "claimVersion={}, claimType={}, redelivered={}",
                    message.generationId(),
                    message.tripId(),
                    claim.claimVersion(),
                    claim.claimVersion() > 1 ? "recovery" : "initial",
                    redelivered
            );

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
                boolean applied = generationService.collectCandidates(
                        message.tripId(),
                        message.generationId(),
                        claimVersion
                );
                if (!applied) {
                    metrics.recordFenced("candidate_save");
                    log.warn(
                            "FENCED_STALE_WORKER_RESULT: generationId={}, tripId={}, "
                                    + "claimVersion={}, operation=candidate_save",
                            message.generationId(),
                            message.tripId(),
                            claimVersion
                    );
                }
                return applied;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                classifiedFailure = failureClassifier.classify(exception);
                String classification = classifiedFailure.retryable() ? RETRYABLE : NON_RETRYABLE;
                metrics.recordFailureAttempt(classification, classifiedFailure.reason());
                log.warn(
                        "Itinerary generation candidate attempt failed: generationId={}, tripId={}, "
                                + "attempt={}, maxAttempts={}, classification={}, failureCode={}",
                        message.generationId(),
                        message.tripId(),
                        attempt,
                        properties.getMaxAttempts(),
                        classification,
                        classifiedFailure.reason()
                );
                if (!classifiedFailure.retryable()) {
                    break;
                }
                if (attempt < properties.getMaxAttempts()) {
                    metrics.recordRetry(classification, classifiedFailure.reason());
                }
            }
        }

        boolean failed = persistenceService.markFailed(
                message.generationId(),
                claimVersion,
                classifiedFailure.reason()
        );
        if (failed) {
            log.error(
                    "Itinerary generation marked FAILED after worker attempts: generationId={}, tripId={}, "
                            + "maxAttempts={}, classification={}, failureCode={}",
                    message.generationId(),
                    message.tripId(),
                    properties.getMaxAttempts(),
                    classifiedFailure.retryable() ? RETRYABLE : NON_RETRYABLE,
                    classifiedFailure.reason()
            );
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
