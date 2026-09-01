package com.planmate.invitation.service;

import com.planmate.invitation.entity.TripInvitationEntity;

/** Private realtime payload for {@code INVITATION_RECEIVED} — no personal data beyond ids. */
public record TripInvitationSummary(Long invitationId, String tripId) {

    public static TripInvitationSummary from(TripInvitationEntity entity) {
        return new TripInvitationSummary(entity.getId(), entity.getTripId().toString());
    }
}
