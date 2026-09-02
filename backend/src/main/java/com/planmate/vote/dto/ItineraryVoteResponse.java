package com.planmate.vote.dto;

import com.planmate.proposal.dto.ItineraryProposalResponse;
import com.planmate.vote.entity.BallotChoice;
import com.planmate.vote.entity.ItineraryVoteEntity;
import java.time.Instant;

public record ItineraryVoteResponse(
        String voteId,
        String tripId,
        ItineraryProposalResponse proposal,
        String status,
        int eligibleVoterCount,
        int minimumParticipationCount,
        int participationCount,
        int changeCount,
        int keepCurrentCount,
        boolean eligibleByMe,
        BallotChoice myChoice,
        Instant deadline,
        Instant closedAt,
        String resultReason,
        Instant createdAt
) {
    public static int minimumParticipation(int eligibleVoters) {
        if (eligibleVoters <= 1) return eligibleVoters;
        return Math.max(2, (eligibleVoters + 1) / 2);
    }
}
