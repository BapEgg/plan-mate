package com.planmate.revision.api.event;

public record ItineraryRevisionAppliedEvent(
        Long tripId,
        Long itineraryId,
        int itineraryVersion,
        Long proposalId
) {
}
