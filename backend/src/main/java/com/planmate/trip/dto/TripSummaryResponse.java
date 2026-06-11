package com.planmate.trip.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TripSummaryResponse(
        String id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        long memberCount,
        Instant createdAt
) {
}
