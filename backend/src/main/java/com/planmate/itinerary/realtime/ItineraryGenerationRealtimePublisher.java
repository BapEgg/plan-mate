package com.planmate.itinerary.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ItineraryGenerationRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ItineraryGenerationRealtimeEventMapper eventMapper;

    public ItineraryGenerationRealtimePublisher(
            SimpMessagingTemplate messagingTemplate,
            ItineraryGenerationRealtimeEventMapper eventMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.eventMapper = eventMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ItineraryGenerationStatusChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/trips/" + event.tripId() + "/events",
                eventMapper.toEnvelope(event)
        );
    }
}
