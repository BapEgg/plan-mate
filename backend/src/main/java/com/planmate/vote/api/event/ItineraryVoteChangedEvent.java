package com.planmate.vote.api.event;

public record ItineraryVoteChangedEvent(Long tripId, Long voteId, Long proposalId, String status) {
}
