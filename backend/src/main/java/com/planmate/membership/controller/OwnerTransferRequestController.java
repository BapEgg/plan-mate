package com.planmate.membership.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.membership.service.OwnerTransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner-transfer-requests/{requestId}")
public class OwnerTransferRequestController {

    private final OwnerTransferService ownerTransferService;

    public OwnerTransferRequestController(OwnerTransferService ownerTransferService) {
        this.ownerTransferService = ownerTransferService;
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        ownerTransferService.accept(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        ownerTransferService.decline(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        ownerTransferService.cancel(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }
}
