package com.planmate.realtime.vote;

public record RevisionAppliedPayload(Long itineraryId, int itineraryVersion, Long proposalId) {
}
