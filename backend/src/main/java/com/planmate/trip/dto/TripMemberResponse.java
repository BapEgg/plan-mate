package com.planmate.trip.dto;

import com.planmate.trip.entity.TripMemberRole;

public record TripMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        TripMemberRole role
) {
}
