package com.planmate.realtime.presence;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class PresenceRealtimeSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public PresenceRealtimeSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handle(MemberPresenceChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/trips/" + event.tripId() + "/events",
                RealtimeEventEnvelope.create(
                        RealtimeEventType.MEMBER_PRESENCE_CHANGED,
                        event.tripId(),
                        event.changedAtUtc(),
                        new MemberPresenceChangedPayload(
                                event.memberId(), event.status(), event.changedAtUtc(), event.eventSequence()
                        )
                )
        );
    }
}
