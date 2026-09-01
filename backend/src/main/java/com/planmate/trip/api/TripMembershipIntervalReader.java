package com.planmate.trip.api;

import java.time.Instant;

/**
 * WP-D phase 3: 호출자의 현재 ACTIVE membership interval이 언제 시작됐는지(=재가입 시 새로
 * 생기는 행의 joinedAt) 노출한다. 채팅 history/unread를 이 시점 이후 message로만 제한할 때 쓴다.
 */
public interface TripMembershipIntervalReader {

    Instant currentIntervalStartedAt(Long userId, Long tripId);

    /** Null means nothing has been read yet in this membership interval. */
    Long currentLastReadChatMessageId(Long userId, Long tripId);
}
