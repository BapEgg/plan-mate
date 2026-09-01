package com.planmate.membership.api.event;

public enum MembershipChangeType {

    /** 서버가 강제로 STOMP session을 끊어야 하는 변경. */
    REMOVED,
    LEFT,

    /** 다른 멤버에게 broadcast만 하면 되는 변경(session 유지). */
    JOINED,
    OWNER_TRANSFERRED,
    TITLE_UPDATED

}
