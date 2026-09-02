package com.planmate.common.realtime;

/**
 * C0: trip topic({@code /topic/trips/{tripId}/events})과 개인 destination
 * ({@code /user/{userId}/queue/trips/{tripId}/events})에서 쓰는 {@link RealtimeEventEnvelope#type()}
 * 값의 단일 registry. 새 값은 여기와 {@code docs/api/collaboration-workspace-api.md}를 함께
 * 바꾼다(contract test가 drift를 잡는다).
 * <p>
 * WP-A가 실제로 발행하는 값은 {@code ITINERARY_GENERATION_STATUS_CHANGED} 하나뿐이다. 나머지는
 * 소비 package가 command를 구현할 때 채울 예약된 이름이며, endpoint·error registry와 함께
 * 먼저 여기서 고정한다(spec §10.1 C0).
 */
public final class RealtimeEventType {

    /** WP-A, 이미 발행됨. {@code com.planmate.realtime.itinerary.ItineraryGenerationRealtimeEventMapper}. */
    public static final String ITINERARY_GENERATION_STATUS_CHANGED = "ITINERARY_GENERATION_STATUS_CHANGED";

    /** WP-B 예약. trip topic. */
    public static final String MEMBERSHIP_CHANGED = "MEMBERSHIP_CHANGED";

    /** WP-B 예약. 개인 destination. */
    public static final String INVITATION_RECEIVED = "INVITATION_RECEIVED";

    /** WP-D 예약. trip topic(payload는 append-only message만 즉시 merge, 나머지는 REST로 복구). */
    public static final String CHAT_MESSAGE_SENT = "CHAT_MESSAGE_SENT";

    /** WP-D. trip topic, client는 해당 message REST snapshot을 다시 조회한다. */
    public static final String CHAT_MESSAGE_DELETED = "CHAT_MESSAGE_DELETED";

    /** WP-D. trip topic, client는 사용자별 reactedByMe가 포함된 message REST snapshot을 다시 조회한다. */
    public static final String CHAT_REACTION_CHANGED = "CHAT_REACTION_CHANGED";

    /** WP-D 예약. 개인 destination. */
    public static final String CHAT_UNREAD_CHANGED = "CHAT_UNREAD_CHANGED";

    /** WP-D. trip topic, ephemeral composer activity without message body. */
    public static final String CHAT_TYPING_UPDATED = "CHAT_TYPING_UPDATED";

    /** WP-D. trip topic, authenticated workspace subscription presence. */
    public static final String MEMBER_PRESENCE_CHANGED = "MEMBER_PRESENCE_CHANGED";

    /** WP-E 예약. trip topic. */
    public static final String VOTE_OPENED = "VOTE_OPENED";

    /** WP-E 예약. trip topic. */
    public static final String VOTE_CLOSED = "VOTE_CLOSED";

    /** WP-E 예약. trip topic — current pointer가 바뀌었으니 REST로 다시 조회하라는 신호만 담는다. */
    public static final String ITINERARY_REVISION_APPLIED = "ITINERARY_REVISION_APPLIED";

    private RealtimeEventType() {
    }
}
