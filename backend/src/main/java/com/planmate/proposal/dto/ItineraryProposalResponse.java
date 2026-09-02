package com.planmate.proposal.dto;

import com.planmate.proposal.entity.ItineraryProposalEntity;
import java.time.Instant;
import java.time.LocalTime;

public record ItineraryProposalResponse(
        String proposalId,
        String tripId,
        Long baseItineraryId,
        int baseItineraryVersion,
        Long createdByUserId,
        String proposalType,
        String status,
        String decisionMode,
        int dayNumber,
        Long targetItemId,
        String replacementPlaceId,
        String replacementDisplayName,
        LocalTime replacementStartTime,
        int replacementDurationMinutes,
        Long appliedItineraryId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ItineraryProposalResponse from(ItineraryProposalEntity proposal) {
        return new ItineraryProposalResponse(
                proposal.getId().toString(),
                proposal.getTripId().toString(),
                proposal.getBaseItineraryId(),
                proposal.getBaseItineraryVersion(),
                proposal.getCreatedByUserId(),
                proposal.getProposalType(),
                proposal.getStatus().name(),
                proposal.getDecisionMode() == null ? null : proposal.getDecisionMode().name(),
                proposal.getDayNumber(),
                proposal.getTargetItemId(),
                proposal.getReplacementPlaceId(),
                proposal.getReplacementDisplayName(),
                proposal.getReplacementStartTime(),
                proposal.getReplacementDurationMinutes(),
                proposal.getAppliedItineraryId(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt()
        );
    }
}
