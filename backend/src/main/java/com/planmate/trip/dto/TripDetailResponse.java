package com.planmate.trip.dto;

import com.planmate.itinerary.dto.ItineraryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TripDetailResponse(
        String id,
        String title,
        String destination,
        String destinationPlaceId,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        long memberCount,
        Instant createdAt,
        List<TripMemberResponse> members,
        TripDestinationResponse destinationInfo,
        TripPlanningProfileResponse planningProfile,
        List<ItineraryResponse> itineraries
) {
}
