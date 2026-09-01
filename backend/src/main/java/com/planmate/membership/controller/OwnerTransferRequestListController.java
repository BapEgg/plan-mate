package com.planmate.membership.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.membership.dto.OwnerTransferRequestResponse;
import com.planmate.membership.service.OwnerTransferService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인박스가 소비하는, 나에게 온 PENDING 방장 이전 요청 목록. */
@RestController
@RequestMapping("/api/owner-transfer-requests")
public class OwnerTransferRequestListController {

    private final OwnerTransferService ownerTransferService;

    public OwnerTransferRequestListController(OwnerTransferService ownerTransferService) {
        this.ownerTransferService = ownerTransferService;
    }

    @GetMapping
    public List<OwnerTransferRequestResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ownerTransferService.listIncoming(user.userId()).stream()
                .map(OwnerTransferRequestResponse::from)
                .toList();
    }
}
