package com.planmate.realtime;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * ADR-0003: 어느 STOMP session이 어느 trip topic을 구독 중인지 in-memory로 추적한다. 단일
 * instance 배포를 전제로 하며(ADR-0004), {@link RealtimeSessionRevocationService}가 멤버십을
 * 잃은 사용자의 session을 강제로 끊을 때 이 registry로 대상을 찾는다.
 */
@Component
public class RealtimeSessionRegistry {

    private final ConcurrentHashMap<String, SessionState> sessionsById = new ConcurrentHashMap<>();

    public void registerSubscription(String sessionId, Long userId, Long tripId) {
        if (sessionId == null) {
            return;
        }
        sessionsById.compute(sessionId, (id, existing) -> {
            SessionState state = existing != null ? existing : new SessionState(userId);
            state.tripIds.add(tripId);
            return state;
        });
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessionsById.remove(sessionId);
        }
    }

    /**
     * 주어진 (tripId, userId) 조합을 구독 중인 모든 session id를 반환한다. 존재하지 않으면
     * 빈 집합을 반환한다 — 이미 끊긴 session에 대한 revoke 호출은 no-op이어야 한다.
     */
    public Set<String> findSessionIds(Long tripId, Long userId) {
        Set<String> matches = new HashSet<>();
        sessionsById.forEach((sessionId, state) -> {
            if (state.userId.equals(userId) && state.tripIds.contains(tripId)) {
                matches.add(sessionId);
            }
        });
        return Collections.unmodifiableSet(matches);
    }

    private static final class SessionState {
        private final Long userId;
        private final Set<Long> tripIds = ConcurrentHashMap.newKeySet();

        private SessionState(Long userId) {
            this.userId = userId;
        }
    }
}
