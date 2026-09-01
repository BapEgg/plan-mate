package com.planmate.realtime.membership;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MembershipRealtimeEventMapper {

    private final Clock clock;

    public MembershipRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<MembershipChangedPayload> toEnvelope(TripMembershipChangedEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.MEMBERSHIP_CHANGED,
                event.tripId(),
                Instant.now(clock),
                new MembershipChangedPayload(event.affectedUserId(), event.changeType())
        );
    }
}
