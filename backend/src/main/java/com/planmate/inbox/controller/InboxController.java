package com.planmate.inbox.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.friend.entity.FriendRequestStatus;
import com.planmate.friend.repository.FriendRequestRepository;
import com.planmate.inbox.dto.InboxSummaryResponse;
import com.planmate.invitation.entity.InvitationStatus;
import com.planmate.invitation.repository.TripInvitationRepository;
import com.planmate.membership.entity.OwnerTransferRequestStatus;
import com.planmate.membership.repository.OwnerTransferRequestRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WP-B: 메인페이지 봉투 button의 badge 숫자. */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final TripInvitationRepository tripInvitationRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final OwnerTransferRequestRepository ownerTransferRequestRepository;

    public InboxController(
            TripInvitationRepository tripInvitationRepository,
            FriendRequestRepository friendRequestRepository,
            OwnerTransferRequestRepository ownerTransferRequestRepository
    ) {
        this.tripInvitationRepository = tripInvitationRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.ownerTransferRequestRepository = ownerTransferRequestRepository;
    }

    @GetMapping("/summary")
    public InboxSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return new InboxSummaryResponse(
                tripInvitationRepository.countByInviteeUserIdAndStatus(user.userId(), InvitationStatus.PENDING),
                friendRequestRepository.countByAddresseeUserIdAndStatus(user.userId(), FriendRequestStatus.PENDING),
                ownerTransferRequestRepository.countByToUserIdAndStatus(user.userId(), OwnerTransferRequestStatus.PENDING)
        );
    }
}
