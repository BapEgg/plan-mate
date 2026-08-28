package com.planmate.tripdetail.dto;

import com.planmate.trip.api.TripDetailTrip;
import com.planmate.trip.dto.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
        List<TripDetailTrip.Member> members,
        TripDetailTrip.DestinationInfo destinationInfo,
        TripDetailTrip.PlanningProfile planningProfile,
        List<Itinerary> itineraries
) {

    public record Itinerary(
            Long id,
            Long generationId,
            Instant createdAt,
            List<Day> days
    ) {
    }

    public record Day(
            Long id,
            int day,
            LocalDate date,
            List<Item> items
    ) {
    }

    public record Item(
            Long id,
            int sequence,
            String placeId,
            LocalTime startTime,
            int durationMinutes,
            String createdSource
    ) {
    }
}
