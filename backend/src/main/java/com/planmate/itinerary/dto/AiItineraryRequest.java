package com.planmate.itinerary.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AiItineraryRequest(
        String generationId,
        String tripId,
        Destination destination,
        LocalDate startDate,
        LocalDate endDate,
        Companion companion,
        Budget budget,
        String travelPace,
        List<String> interests,
        Transportation transportation,
        Accommodation accommodation,
        List<MustVisitPlace> mustVisitPlaces,
        List<String> avoidConditions,
        String freeRequest,
        List<String> rules
) {

    public record Destination(
            String placeId,
            String name,
            String formattedAddress,
            Double latitude,
            Double longitude,
            List<String> types,
            String primaryType
    ) {
    }

    public record Companion(
            int count,
            String type,
            boolean hasChildren,
            int childCount,
            String childAgeGroup,
            boolean hasSeniors,
            int seniorCount
    ) {
    }

    public record Budget(
            String currencyCode,
            Long amount,
            String level,
            List<String> includedItems
    ) {
    }

    public record Transportation(
            String primaryMode,
            List<String> secondaryModes
    ) {
    }

    public record Accommodation(
            String mode,
            String preferredArea,
            String name,
            LocalTime checkInTime,
            LocalTime checkOutTime
    ) {
    }

    public record MustVisitPlace(
            String placeId,
            String name,
            String formattedAddress,
            Double latitude,
            Double longitude
    ) {
    }

}
