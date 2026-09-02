package com.planmate.proposal.api.event;

public record ItineraryProposalChangedEvent(Long tripId, Long proposalId, String status) {
}
