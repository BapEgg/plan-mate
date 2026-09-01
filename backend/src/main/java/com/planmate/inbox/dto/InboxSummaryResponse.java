package com.planmate.inbox.dto;

public record InboxSummaryResponse(
        long tripInvitationCount,
        long friendRequestCount,
        long ownerTransferRequestCount
) {
}
