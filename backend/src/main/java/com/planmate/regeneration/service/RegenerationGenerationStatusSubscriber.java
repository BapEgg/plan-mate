package com.planmate.regeneration.service;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegenerationGenerationStatusSubscriber {

    private final ItineraryRegenerationService service;

    public RegenerationGenerationStatusSubscriber(ItineraryRegenerationService service) {
        this.service = service;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryGenerationStatusChangedEvent event) {
        if (event.status() == ItineraryGenerationStatus.FAILED) {
            service.markGenerationFailed(event.generationId(), event.failureReason());
        }
    }
}
