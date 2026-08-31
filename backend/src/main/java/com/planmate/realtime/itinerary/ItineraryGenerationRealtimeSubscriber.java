package com.planmate.realtime.itinerary;

import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ItineraryGenerationRealtimeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationRealtimeSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ItineraryGenerationRealtimeEventMapper eventMapper;

    public ItineraryGenerationRealtimeSubscriber(
            SimpMessagingTemplate messagingTemplate,
            ItineraryGenerationRealtimeEventMapper eventMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.eventMapper = eventMapper;
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryGenerationStatusChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trips/" + event.tripId() + "/events",
                    eventMapper.toEnvelope(event)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish itinerary generation realtime event. tripId={}, generationId={}, status={}",
                    event.tripId(),
                    event.generationId(),
                    event.status(),
                    exception
            );
        }
    }
}
