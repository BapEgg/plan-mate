package com.planmate.friend.dto;

public record CreateFriendRequestRequest(Long addresseeUserId, String addresseeEmail) {
}
