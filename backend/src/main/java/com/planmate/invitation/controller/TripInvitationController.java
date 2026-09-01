package com.planmate.invitation.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.invitation.dto.CreateTripInvitationRequest;
import com.planmate.invitation.dto.TripInvitationResponse;
import com.planmate.invitation.entity.TripInvitationEntity;
import com.planmate.invitation.service.TripInvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/invitations")
public class TripInvitationController {

    private final TripInvitationService tripInvitationService;

    public TripInvitationController(TripInvitationService tripInvitationService) {
        this.tripInvitationService = tripInvitationService;
    }

    @PostMapping
    public ResponseEntity<TripInvitationResponse> send(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody CreateTripInvitationRequest request
    ) {
        TripInvitationEntity invitation = tripInvitationService.send(
                user.userId(), tripId, request.inviteeUserId(), request.inviteeEmail()
        );
        return ResponseEntity.ok(TripInvitationResponse.from(invitation));
    }
}
