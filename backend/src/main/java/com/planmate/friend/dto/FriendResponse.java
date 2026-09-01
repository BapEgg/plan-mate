package com.planmate.friend.dto;

public record FriendResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {
}
