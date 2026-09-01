package com.planmate.trip.api;

/**
 * WP-D phase 3: 현재 ACTIVE membership interval 위에 채팅 읽음 위치를 기록한다. 나가기/재가입은
 * 새 interval 행을 만들므로 읽음 상태도 함께 초기화된다(ADR-0001).
 */
public interface TripMembershipChatReadTracker {

    void markChatRead(Long userId, Long tripId, Long messageId);
}
