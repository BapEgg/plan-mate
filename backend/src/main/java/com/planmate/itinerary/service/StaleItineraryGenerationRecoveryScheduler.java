package com.planmate.itinerary.service;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.messaging.ItineraryGenerationRecoveryPublisher;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.itinerary.generation-worker",
        name = {"enabled", "stale-recovery-enabled"},
        havingValue = "true"
)
public class StaleItineraryGenerationRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleItineraryGenerationRecoveryScheduler.class);

    private final ItineraryGenerationRepository generationRepository;
    private final ItineraryGenerationRecoveryPublisher recoveryPublisher;
    private final ItineraryGenerationWorkerProperties properties;
    private final ItineraryGenerationWorkerMetrics metrics;
    private final Clock clock;

    public StaleItineraryGenerationRecoveryScheduler(
            ItineraryGenerationRepository generationRepository,
            ItineraryGenerationRecoveryPublisher recoveryPublisher,
            ItineraryGenerationWorkerProperties properties,
            ItineraryGenerationWorkerMetrics metrics,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.recoveryPublisher = recoveryPublisher;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.itinerary.generation-worker.recovery-scan-interval:1m}")
    public void recoverStaleCollections() {
        Instant now = Instant.now(clock);
        List<ItineraryGenerationEntity> staleGenerations = generationRepository.findStaleCollections(
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                now,
                PageRequest.of(0, properties.getRecoveryBatchSize())
        );
        for (ItineraryGenerationEntity generation : staleGenerations) {
            try {
                recoveryPublisher.publish(generation.getId(), generation.getTripId());
                metrics.recordRecoveryPublished();
                log.info(
                        "Stale itinerary generation recovery published: generationId={}, tripId={}, claimVersion={}",
                        generation.getId(),
                        generation.getTripId(),
                        generation.getCollectionClaimVersion()
                );
            } catch (RuntimeException exception) {
                log.warn("Failed to publish stale itinerary generation recovery: generationId={}",
                        generation.getId(), exception);
            }
        }
    }
}
