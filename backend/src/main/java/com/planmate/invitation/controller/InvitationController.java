package com.planmate.invitation.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.invitation.dto.TripInvitationResponse;
import com.planmate.invitation.service.TripInvitationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final TripInvitationService tripInvitationService;

    public InvitationController(TripInvitationService tripInvitationService) {
        this.tripInvitationService = tripInvitationService;
    }

    @GetMapping
    public List<TripInvitationResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return tripInvitationService.listMine(user.userId()).stream()
                .map(TripInvitationResponse::from)
                .toList();
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long invitationId) {
        tripInvitationService.accept(user.userId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long invitationId) {
        tripInvitationService.decline(user.userId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long invitationId) {
        tripInvitationService.cancel(user.userId(), invitationId);
        return ResponseEntity.noContent().build();
    }
}
