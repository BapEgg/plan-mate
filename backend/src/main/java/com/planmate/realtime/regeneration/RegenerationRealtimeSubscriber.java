package com.planmate.realtime.regeneration;

import com.planmate.regeneration.api.event.ItineraryRegenerationChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegenerationRealtimeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RegenerationRealtimeSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final RegenerationRealtimeEventMapper mapper;

    public RegenerationRealtimeSubscriber(
            SimpMessagingTemplate messagingTemplate,
            RegenerationRealtimeEventMapper mapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryRegenerationChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trips/" + event.tripId() + "/events",
                    mapper.toEnvelope(event)
            );
        } catch (RuntimeException exception) {
            log.error("Failed to publish itinerary regeneration event. tripId={}", event.tripId(), exception);
        }
    }
}
