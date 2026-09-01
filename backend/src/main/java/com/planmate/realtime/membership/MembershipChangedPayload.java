package com.planmate.realtime.membership;

import com.planmate.membership.api.event.MembershipChangeType;

public record MembershipChangedPayload(
        Long affectedUserId,
        MembershipChangeType changeType
) {
}
