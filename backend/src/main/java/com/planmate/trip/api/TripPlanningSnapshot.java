package com.planmate.trip.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record TripPlanningSnapshot(
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

    public TripPlanningSnapshot {
        mustVisitPlaces = copyOrEmpty(mustVisitPlaces);
        avoidConditions = copyOrEmpty(avoidConditions);
    }

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

        public boolean isResolved() {
            return placeId != null && !placeId.isBlank() && latitude != null && longitude != null;
        }
    }
}
