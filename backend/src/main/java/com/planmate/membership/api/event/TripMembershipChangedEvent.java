package com.planmate.membership.api.event;

/**
 * WP-B: {@code realtime.membership}이 이 event를 받아 trip topic broadcast와(REMOVED/LEFT의 경우)
 * {@code RealtimeSessionRevocationService} 호출을 수행한다. {@code affectedUserId}는
 * REMOVED/LEFT/OWNER_TRANSFERRED(새 OWNER)일 때만 의미가 있고, 그 외에는 null일 수 있다.
 */
public record TripMembershipChangedEvent(
        Long tripId,
        Long affectedUserId,
        MembershipChangeType changeType
) {
}
