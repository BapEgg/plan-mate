package com.planmate.regeneration.dto;

import com.planmate.regeneration.entity.ItineraryRegenerationStatus;
import com.planmate.regeneration.entity.RegenerationScopeType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ItineraryRegenerationResponse(
        Long regenerationId,
        String tripId,
        String generationId,
        Long baseItineraryId,
        int baseItineraryVersion,
        RegenerationScopeType scope,
        Integer dayNumber,
        Long startItemId,
        Long endItemId,
        List<Long> fixedItemIds,
        ItineraryRegenerationStatus status,
        String failureReason,
        Long appliedItineraryId,
        List<DayComparison> days,
        Instant createdAt,
        Instant updatedAt
) {
    public record DayComparison(int day, LocalDate date, List<ItemComparison> items) {
    }

    public record ItemComparison(
            int sequence,
            Long originalItemId,
            String originalPlaceId,
            String originalDisplayName,
            LocalTime originalStartTime,
            Integer originalDurationMinutes,
            String proposedPlaceId,
            String proposedDisplayName,
            LocalTime proposedStartTime,
            int proposedDurationMinutes,
            boolean fixed,
            boolean changed
    ) {
    }
}
