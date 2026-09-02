package com.planmate.realtime.vote;

import com.planmate.revision.api.event.ItineraryRevisionAppliedEvent;
import com.planmate.vote.api.event.ItineraryVoteChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VoteRealtimeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(VoteRealtimeSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final VoteRealtimeEventMapper mapper;

    public VoteRealtimeSubscriber(SimpMessagingTemplate messagingTemplate, VoteRealtimeEventMapper mapper) {
        this.messagingTemplate = messagingTemplate;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryVoteChangedEvent event) {
        publish(event.tripId(), mapper.toEnvelope(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItineraryRevisionAppliedEvent event) {
        publish(event.tripId(), mapper.toEnvelope(event));
    }

    private void publish(Long tripId, Object envelope) {
        try {
            messagingTemplate.convertAndSend("/topic/trips/" + tripId + "/events", envelope);
        } catch (RuntimeException exception) {
            log.error("Failed to publish itinerary vote/revision event. tripId={}", tripId, exception);
        }
    }
}
