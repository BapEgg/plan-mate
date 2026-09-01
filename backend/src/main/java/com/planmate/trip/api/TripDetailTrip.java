package com.planmate.trip.api;

import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.ChildAgeGroup;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.dto.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TripDetailTrip(
        Long id,
        String title,
        String destination,
        String destinationPlaceId,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        long memberCount,
        Instant createdAt,
        List<Member> members,
        DestinationInfo destinationInfo,
        PlanningProfile planningProfile,
        String timezone
) {

    public record Member(
            Long userId,
            String nickname,
            String profileImageUrl,
            String role
    ) {
    }

    public record DestinationInfo(
            String placeId,
            String displayName,
            String formattedAddress,
            Double latitude,
            Double longitude,
            Double viewportLowLatitude,
            Double viewportLowLongitude,
            Double viewportHighLatitude,
            Double viewportHighLongitude,
            List<String> types,
            String primaryType
    ) {
    }

    public record PlanningProfile(
            int companionCount,
            CompanionType companionType,
            boolean hasChildren,
            int childCount,
            ChildAgeGroup childAgeGroup,
            boolean hasSeniors,
            int seniorCount,
            CurrencyCode currencyCode,
            Long budgetAmount,
            BudgetLevel budgetLevel,
            List<BudgetItem> includedBudgetItems,
            TravelPace travelPace,
            List<TripInterest> interests,
            TransportMode primaryTransportMode,
            List<TransportMode> secondaryTransportModes,
            AccommodationMode accommodationMode,
            AccommodationArea accommodationArea,
            String accommodationName,
            String accommodationPlaceId,
            String accommodationFormattedAddress,
            Double accommodationLatitude,
            Double accommodationLongitude,
            List<String> accommodationTypes,
            String accommodationPrimaryType,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            List<MustVisitPlaceSnapshot> mustVisitPlaces,
            List<AvoidCondition> avoidConditions,
            String freeRequest
    ) {
    }
}
