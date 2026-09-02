package com.planmate.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        DailyWindow dailyWindow,
        List<MustVisitPlace> mustVisitPlaces,
        List<String> avoidConditions,
        String freeRequest,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Candidate> candidates,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> rules,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Regeneration regeneration
) {

    public AiItineraryRequest {
        interests = copyOrEmpty(interests);
        mustVisitPlaces = copyOrEmpty(mustVisitPlaces);
        avoidConditions = copyOrEmpty(avoidConditions);
        candidates = copyOrEmpty(candidates);
        rules = copyOrEmpty(rules);
    }

    public record Destination(
            String placeId,
            String name,
            String formattedAddress,
            Double latitude,
            Double longitude,
            List<String> types,
            String primaryType
    ) {

        public Destination {
            types = copyOrEmpty(types);
        }
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

        public Budget {
            includedItems = copyOrEmpty(includedItems);
        }
    }

    public record Transportation(
            String primaryMode,
            List<String> secondaryModes
    ) {

        public Transportation {
            secondaryModes = copyOrEmpty(secondaryModes);
        }
    }

    public record Accommodation(
            String mode,
            String preferredArea,
            String name,
            LocalTime checkInTime,
            LocalTime checkOutTime
    ) {
    }

    public record DailyWindow(
            LocalTime startTime,
            LocalTime endTime
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

    public record Candidate(
            int rank,
            String placeId,
            String displayName,
            String formattedAddress,
            Double latitude,
            Double longitude,
            String primaryType,
            List<String> types,
            List<String> openingPeriods,
            boolean forcedMustVisit
    ) {

        public Candidate {
            types = copyOrEmpty(types);
            openingPeriods = copyOrEmpty(openingPeriods);
        }
    }

    public record Regeneration(
            String scope,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds,
            String additionalRequest,
            List<CurrentItem> currentItems
    ) {
        public Regeneration {
            fixedItemIds = copyOrEmpty(fixedItemIds);
            currentItems = copyOrEmpty(currentItems);
        }
    }

    public record CurrentItem(
            Long itemId,
            int day,
            int sequence,
            String placeId,
            String startTime,
            int durationMinutes,
            String action
    ) {
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
