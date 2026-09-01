package com.planmate.membership.dto;

import com.planmate.membership.entity.OwnerTransferRequestEntity;
import com.planmate.membership.entity.OwnerTransferRequestStatus;
import java.time.Instant;

public record OwnerTransferRequestResponse(
        Long id,
        String tripId,
        Long fromUserId,
        Long toUserId,
        OwnerTransferRequestStatus status,
        Instant createdAt,
        Instant expiresAt
) {
    public static OwnerTransferRequestResponse from(OwnerTransferRequestEntity entity) {
        return new OwnerTransferRequestResponse(
                entity.getId(),
                entity.getTripId().toString(),
                entity.getFromUserId(),
                entity.getToUserId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
