package com.planmate.realtime.vote;

public record VoteChangedPayload(Long voteId, Long proposalId, String status) {
}
