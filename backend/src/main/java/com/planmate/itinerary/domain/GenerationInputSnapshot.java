package com.planmate.itinerary.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record GenerationInputSnapshot(
        Long tripId,
        LocalDate startDate,
        LocalDate endDate,
        Destination destination,
        Companion companion,
        Budget budget,
        Preference preference,
        Transportation transportation,
        Accommodation accommodation,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        List<MustVisitPlace> mustVisitPlaces,
        List<String> avoidConditions,
        String freeRequest
) {

    public GenerationInputSnapshot {
        mustVisitPlaces = copyOrEmpty(mustVisitPlaces);
        avoidConditions = copyOrEmpty(avoidConditions);
    }

    @JsonIgnore
    public int tripDayCount() {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Destination(
            String placeId,
            String displayName,
            String formattedAddress,
            Double latitude,
            Double longitude,
            Viewport viewport,
            List<String> types,
            String primaryType
    ) {

        public Destination {
            types = copyOrEmpty(types);
        }

        @JsonIgnore
        public boolean isResolved() {
            return latitude != null && longitude != null;
        }
    }

    public record Viewport(
            Double lowLatitude,
            Double lowLongitude,
            Double highLatitude,
            Double highLongitude
    ) {
    }

    public record Companion(
            int companionCount,
            String companionType,
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

    public record Preference(
            String travelPace,
            List<String> interests
    ) {

        public Preference {
            interests = copyOrEmpty(interests);
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
            String accommodationMode,
            String preferredArea,
            String placeId,
            String name,
            String formattedAddress,
            Double latitude,
            Double longitude,
            List<String> types,
            String primaryType,
            LocalTime checkInTime,
            LocalTime checkOutTime
    ) {

        public Accommodation {
            types = copyOrEmpty(types);
        }
    }

    public record MustVisitPlace(
            String placeId,
            String name,
            String formattedAddress,
            Double latitude,
            Double longitude,
            List<String> types,
            String primaryType
    ) {

        public MustVisitPlace {
            types = copyOrEmpty(types);
        }

        @JsonIgnore
        public boolean isResolved() {
            return placeId != null && !placeId.isBlank() && latitude != null && longitude != null;
        }
    }
}
