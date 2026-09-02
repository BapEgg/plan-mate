package com.planmate.itinerary.fixture;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.RegenerationConstraintProvider;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.GenerationInputSnapshotStore;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("itinerary-fixture")
@ConditionalOnProperty(
        prefix = "app.itinerary.fixture-response",
        name = "enabled",
        havingValue = "true"
)
public class FixtureItineraryResponseSubscriber {

    private static final Logger log = LoggerFactory.getLogger(FixtureItineraryResponseSubscriber.class);

    private final GenerationInputSnapshotStore generationInputSnapshotStore;
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore;
    private final RegenerationConstraintProvider regenerationConstraintProvider;
    private final FixtureItineraryDraftProvider fixtureProvider;
    private final FixtureItineraryResponseExecutor responseExecutor;

    public FixtureItineraryResponseSubscriber(
            GenerationInputSnapshotStore generationInputSnapshotStore,
            GenerationCandidateSnapshotStore generationCandidateSnapshotStore,
            RegenerationConstraintProvider regenerationConstraintProvider,
            FixtureItineraryDraftProvider fixtureProvider,
            FixtureItineraryResponseExecutor responseExecutor
    ) {
        this.generationInputSnapshotStore = generationInputSnapshotStore;
        this.generationCandidateSnapshotStore = generationCandidateSnapshotStore;
        this.regenerationConstraintProvider = regenerationConstraintProvider;
        this.fixtureProvider = fixtureProvider;
        this.responseExecutor = responseExecutor;
    }

    @Order(100)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryGenerationStatusChangedEvent event) {
        if (event.status() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            return;
        }

        try {
            GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(event.generationId());
            Set<String> allowedPlaceIds = new LinkedHashSet<>(generationCandidateSnapshotStore
                    .findAllByGenerationId(event.generationId()).stream()
                    .map(GenerationCandidateSnapshot::placeId)
                    .toList());
            regenerationConstraintProvider.findByGenerationId(event.generationId())
                    .ifPresent(constraint -> constraint.currentItems().stream()
                            .map(RegenerationConstraintProvider.Item::placeId)
                            .forEach(allowedPlaceIds::add));
            Optional<AiItineraryDraft> fixture = fixtureProvider.load(
                    event.generationId(),
                    snapshot.tripDayCount(),
                    allowedPlaceIds
            );
            if (fixture.isEmpty()) {
                log.info(
                        "No itinerary fixture configured for generation: generationId={}, tripId={}, tripDayCount={}",
                        event.generationId(),
                        event.tripId(),
                        snapshot.tripDayCount()
                );
                return;
            }

            responseExecutor.submit(event.tripId(), event.generationId(), fixture.get());
            log.info(
                    "Itinerary fixture response completed: generationId={}, tripId={}, tripDayCount={}",
                    event.generationId(),
                    event.tripId(),
                    snapshot.tripDayCount()
            );
        } catch (AiItineraryValidationException exception) {
            log.error(
                    "Itinerary fixture response failed validation: generationId={}, tripId={}, errors={}",
                    event.generationId(),
                    event.tripId(),
                    exception.validationReport().errors()
            );
        } catch (RuntimeException exception) {
            // Semantic response failures intentionally leave the generation READY so the stored inputs and
            // candidates remain inspectable and the existing manual handoff can still be used.
            log.error(
                    "Itinerary fixture response rejected: generationId={}, tripId={}",
                    event.generationId(),
                    event.tripId(),
                    exception
            );
        }
    }
}
