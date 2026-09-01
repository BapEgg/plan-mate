package com.planmate.friend.dto;

import com.planmate.friend.entity.FriendRequestEntity;
import com.planmate.friend.entity.FriendRequestStatus;
import java.time.Instant;

public record FriendRequestResponse(
        Long id,
        Long requesterUserId,
        Long addresseeUserId,
        FriendRequestStatus status,
        Instant createdAt
) {
    public static FriendRequestResponse from(FriendRequestEntity entity) {
        return new FriendRequestResponse(
                entity.getId(),
                entity.getRequesterUserId(),
                entity.getAddresseeUserId(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
