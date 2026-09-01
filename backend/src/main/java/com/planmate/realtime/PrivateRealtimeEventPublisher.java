package com.planmate.realtime;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import java.time.Clock;
import java.time.Instant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * ADR-0003: 개인 STOMP destination({@code /user/{userId}/queue/trips/{tripId}/events})으로
 * event를 보낸다. 초대함, 채팅 ack, 개인 unread처럼 다른 참여자에게 노출되면 안 되는 payload가
 * 이 경로를 사용한다(spec §10.5). WP-A는 broker에 {@code /queue}를 활성화하고 이 port를
 * 배선하지만, 실제로 이 port를 호출하는 개인 event는 아직 없다 — WP-B/D가 소비한다.
 */
@Component
public class PrivateRealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public PrivateRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public <T> void sendToUser(Long userId, Long tripId, String eventType, T payload) {
        Instant now = Instant.now(clock);
        RealtimeEventEnvelope<T> envelope = RealtimeEventEnvelope.create(eventType, tripId, now, payload);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/trips/" + tripId + "/events",
                envelope
        );
    }
}
