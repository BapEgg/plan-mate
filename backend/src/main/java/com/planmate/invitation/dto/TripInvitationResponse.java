package com.planmate.invitation.dto;

import com.planmate.invitation.entity.InvitationStatus;
import com.planmate.invitation.entity.TripInvitationEntity;
import java.time.Instant;

public record TripInvitationResponse(
        Long id,
        String tripId,
        Long inviteeUserId,
        Long invitedByUserId,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt
) {
    public static TripInvitationResponse from(TripInvitationEntity entity) {
        return new TripInvitationResponse(
                entity.getId(),
                entity.getTripId().toString(),
                entity.getInviteeUserId(),
                entity.getInvitedByUserId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
