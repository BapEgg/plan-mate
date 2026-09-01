package com.planmate.realtime.membership;

import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.realtime.RealtimeSessionRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * WP-B: WP-A가 만든 {@link RealtimeSessionRevocationService} port의 첫 실제 호출자. 멤버십
 * 변경을 trip topic에 broadcast하고, REMOVED/LEFT는 그 대상 사용자의 이미 연결된 STOMP session을
 * 즉시 끊는다(spec §4.1 "이미 연결된 WebSocket도 멤버십 상실 뒤 새 event를 받지 못해야 한다").
 */
@Component
public class MembershipRealtimeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MembershipRealtimeSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MembershipRealtimeEventMapper eventMapper;
    private final RealtimeSessionRevocationService sessionRevocationService;

    public MembershipRealtimeSubscriber(
            SimpMessagingTemplate messagingTemplate,
            MembershipRealtimeEventMapper eventMapper,
            RealtimeSessionRevocationService sessionRevocationService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.eventMapper = eventMapper;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TripMembershipChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trips/" + event.tripId() + "/events",
                    eventMapper.toEnvelope(event)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish membership realtime event. tripId={}, affectedUserId={}, changeType={}",
                    event.tripId(),
                    event.affectedUserId(),
                    event.changeType(),
                    exception
            );
        }

        if (isRevocationRequired(event)) {
            try {
                sessionRevocationService.revokeTripAccess(event.tripId(), event.affectedUserId());
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to revoke realtime session after membership change. tripId={}, affectedUserId={}",
                        event.tripId(),
                        event.affectedUserId(),
                        exception
                );
            }
        }
    }

    private boolean isRevocationRequired(TripMembershipChangedEvent event) {
        return (event.changeType() == MembershipChangeType.REMOVED || event.changeType() == MembershipChangeType.LEFT)
                && event.affectedUserId() != null;
    }
}
