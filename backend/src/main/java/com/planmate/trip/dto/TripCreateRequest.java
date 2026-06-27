package com.planmate.trip.dto;

import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.ChildAgeGroup;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

public record TripCreateRequest(
        @NotBlank
        @Size(max = 60)
        String title,

        @NotBlank
        @Size(max = 255)
        String destinationPlaceId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @Valid
        @NotNull
        CompanionRequest companion,

        @Valid
        @NotNull
        BudgetRequest budget,

        @Valid
        @NotNull
        PreferenceRequest preferences,

        @Valid
        @NotNull
        TransportationRequest transportation,

        @Valid
        @NotNull
        AccommodationRequest accommodation,

        @Valid
        @NotNull
        SchedulePreferenceRequest schedulePreference,

        @Valid
        @NotNull
        AdditionalRequest additionalRequest
) {

    @AssertTrue(message = "endDate must be on or after startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    public record CompanionRequest(
            @Min(1)
            @Max(20)
            int count,

            @NotNull
            CompanionType type,

            boolean hasChildren,

            @Min(0)
            @Max(20)
            int childCount,

            ChildAgeGroup childAgeGroup,

            boolean hasSeniors,

            @Min(0)
            @Max(20)
            int seniorCount
    ) {

        @AssertTrue(message = "childCount and seniorCount cannot exceed companion count")
        public boolean isDependentCountValid() {
            return normalizedChildCount() + normalizedSeniorCount() <= count;
        }

        @AssertTrue(message = "childAgeGroup is required when children are included")
        public boolean isChildAgeGroupValid() {
            return !hasChildren || childAgeGroup != null;
        }

        public int normalizedChildCount() {
            return hasChildren ? childCount : 0;
        }

        public int normalizedSeniorCount() {
            return hasSeniors ? seniorCount : 0;
        }
    }

    public record BudgetRequest(
            @NotNull
            CurrencyCode currencyCode,

            @Positive
            Long amount,

            @NotNull
            BudgetLevel level,

            @NotNull
            @Size(max = 4)
            List<BudgetItem> includedItems
    ) {

        @AssertTrue(message = "includedItems cannot contain duplicates")
        public boolean isIncludedItemsUnique() {
            return hasNoDuplicates(includedItems);
        }
    }

    public record PreferenceRequest(
            @NotNull
            TravelPace travelPace,

            @NotNull
            @Size(min = 1, max = 5)
            List<TripInterest> interests
    ) {

        @AssertTrue(message = "interests cannot contain duplicates")
        public boolean isInterestsUnique() {
            return hasNoDuplicates(interests);
        }
    }

    public record TransportationRequest(
            @NotNull
            TransportMode primaryMode,

            @NotNull
            @Size(max = 5)
            List<TransportMode> secondaryModes
    ) {

        @AssertTrue(message = "secondaryModes cannot contain duplicates")
        public boolean isSecondaryModesUnique() {
            return hasNoDuplicates(secondaryModes);
        }

        @AssertTrue(message = "secondaryModes cannot include primaryMode")
        public boolean isSecondaryModesDoNotIncludePrimaryMode() {
            return primaryMode == null || secondaryModes == null || !secondaryModes.contains(primaryMode);
        }
    }

    public record AccommodationRequest(
            @NotNull
            AccommodationMode mode,

            AccommodationArea preferredArea,

            @Size(max = 255)
            String placeId,

            LocalTime checkInTime,

            LocalTime checkOutTime
    ) {

        @AssertTrue(message = "preferredArea is required when accommodation is undecided")
        public boolean isPreferredAreaValid() {
            return mode != AccommodationMode.UNDECIDED || preferredArea != null;
        }

        @AssertTrue(message = "placeId must be blank when accommodation is undecided")
        public boolean isUndecidedPlaceIdBlank() {
            return mode != AccommodationMode.UNDECIDED || isBlank(placeId);
        }

        @AssertTrue(message = "placeId is required when accommodation is selected")
        public boolean isSelectedAccommodationValid() {
            if (mode != AccommodationMode.PLACE_SEARCH) {
                return true;
            }
            return !isBlank(placeId);
        }

        @AssertTrue(message = "preferredArea must be null when accommodation is selected")
        public boolean isSelectedPreferredAreaEmpty() {
            return mode != AccommodationMode.PLACE_SEARCH || preferredArea == null;
        }
    }

    public record SchedulePreferenceRequest(
            LocalTime dailyStartTime,
            LocalTime dailyEndTime
    ) {

        @AssertTrue(message = "dailyStartTime must be before dailyEndTime")
        public boolean isDailyTimeRangeValid() {
            return dailyStartTime == null || dailyEndTime == null || dailyStartTime.isBefore(dailyEndTime);
        }
    }

    public record AdditionalRequest(
            @NotNull
            @Size(max = 5)
            List<@NotBlank @Size(max = 255) String> mustVisitPlaceIds,

            @NotNull
            @Size(max = 9)
            List<AvoidCondition> avoidConditions,

            @Size(max = 800)
            String freeRequest
    ) {

        @AssertTrue(message = "mustVisitPlaceIds cannot contain duplicates")
        public boolean isMustVisitPlaceIdsUnique() {
            return mustVisitPlaceIds == null || mustVisitPlaceIds.stream()
                    .map(String::trim)
                    .distinct()
                    .count() == mustVisitPlaceIds.size();
        }

        @AssertTrue(message = "avoidConditions cannot contain duplicates")
        public boolean isAvoidConditionsUnique() {
            return hasNoDuplicates(avoidConditions);
        }
    }

    private static boolean hasNoDuplicates(List<?> values) {
        return values == null || new HashSet<>(values).size() == values.size();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
