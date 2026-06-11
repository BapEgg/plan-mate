package com.planmate.trip.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TripDetailResponse(
        String id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        long memberCount,
        Instant createdAt,
        List<TripMemberResponse> members
) {
}
