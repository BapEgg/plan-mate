package com.planmate.realtime.vote;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import com.planmate.revision.api.event.ItineraryRevisionAppliedEvent;
import com.planmate.vote.api.event.ItineraryVoteChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class VoteRealtimeEventMapper {

    private final Clock clock;

    public VoteRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<VoteChangedPayload> toEnvelope(ItineraryVoteChangedEvent event) {
        String type = "OPEN".equals(event.status())
                ? RealtimeEventType.VOTE_OPENED
                : RealtimeEventType.VOTE_CLOSED;
        return RealtimeEventEnvelope.create(
                type,
                event.tripId(),
                Instant.now(clock),
                new VoteChangedPayload(event.voteId(), event.proposalId(), event.status())
        );
    }

    public RealtimeEventEnvelope<RevisionAppliedPayload> toEnvelope(ItineraryRevisionAppliedEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.ITINERARY_REVISION_APPLIED,
                event.tripId(),
                Instant.now(clock),
                new RevisionAppliedPayload(
                        event.itineraryId(), event.itineraryVersion(), event.proposalId()
                )
        );
    }
}
