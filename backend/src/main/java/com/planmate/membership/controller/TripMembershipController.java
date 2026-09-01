package com.planmate.membership.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.membership.dto.CreateOwnerTransferRequestRequest;
import com.planmate.membership.dto.OwnerTransferRequestResponse;
import com.planmate.membership.dto.UpdateTripTitleRequest;
import com.planmate.membership.entity.OwnerTransferRequestEntity;
import com.planmate.membership.service.OwnerTransferService;
import com.planmate.membership.service.TripMembershipCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class TripMembershipController {

    private final TripMembershipCommandService membershipCommandService;
    private final OwnerTransferService ownerTransferService;

    public TripMembershipController(
            TripMembershipCommandService membershipCommandService,
            OwnerTransferService ownerTransferService
    ) {
        this.membershipCommandService = membershipCommandService;
        this.ownerTransferService = ownerTransferService;
    }

    @PatchMapping("/title")
    public ResponseEntity<Void> updateTitle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody UpdateTripTitleRequest request
    ) {
        membershipCommandService.updateTitle(user.userId(), tripId, request.title());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long userId
    ) {
        membershipCommandService.removeMember(user.userId(), tripId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        membershipCommandService.leave(user.userId(), tripId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/owner-transfer-requests")
    public ResponseEntity<OwnerTransferRequestResponse> createOwnerTransferRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestBody CreateOwnerTransferRequestRequest request
    ) {
        OwnerTransferRequestEntity created = ownerTransferService.create(user.userId(), tripId, request.targetUserId());
        return ResponseEntity.ok(OwnerTransferRequestResponse.from(created));
    }
}
