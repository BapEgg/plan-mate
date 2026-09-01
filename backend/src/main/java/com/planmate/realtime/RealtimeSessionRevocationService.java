package com.planmate.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * ADR-0003: 멤버십을 잃은 사용자의 이미 연결된 STOMP session을 강제로 끊는다.
 * <p>
 * 메커니즘: {@code clientOutboundChannel}로 {@link SimpMessageType#DISCONNECT_ACK} 타입
 * message를 특정 sessionId로 보내면, Spring의
 * {@code StompSubProtocolHandler#getStompHeaderAccessor}가 이를 STOMP {@code ERROR} frame
 * ("Session closed.")으로 변환하고, {@code SubProtocolWebSocketHandler#sendToClient}가 그
 * frame을 보낸 직후 해당 session을 {@code CloseStatus.PROTOCOL_ERROR}로 즉시 close한다
 * (spring-websocket 6.2 bytecode로 확인). 클라이언트의 기존 reconnectDelay가 재연결을
 * 시도하고, {@link RealtimeStompChannelInterceptor}의 SUBSCRIBE-time 멤버십 검사가 재구독을
 * 거부한다 — 그 결과 새 event를 더 이상 받지 못한다.
 * <p>
 * WP-A는 이 서비스와 통합 테스트만 제공한다. 실제 호출자(멤버 제거 command)는 WP-B가
 * 구현한다.
 */
@Service
public class RealtimeSessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionRevocationService.class);

    private final RealtimeSessionRegistry sessionRegistry;
    private final MessageChannel clientOutboundChannel;

    public RealtimeSessionRevocationService(
            RealtimeSessionRegistry sessionRegistry,
            org.springframework.messaging.SubscribableChannel clientOutboundChannel
    ) {
        this.sessionRegistry = sessionRegistry;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    public void revokeTripAccess(Long tripId, Long userId) {
        for (String sessionId : sessionRegistry.findSessionIds(tripId, userId)) {
            disconnect(sessionId);
        }
    }

    private void disconnect(String sessionId) {
        try {
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT_ACK);
            accessor.setSessionId(sessionId);
            accessor.setLeaveMutable(true);
            clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        } catch (RuntimeException exception) {
            log.warn("Failed to force-disconnect realtime session. sessionId={}", sessionId, exception);
        } finally {
            sessionRegistry.removeSession(sessionId);
        }
    }
}
