package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.PlaceCandidateEntity;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryRequestFactory {

    private static final List<String> CANDIDATE_FIELDS = List.of(
            "placeId",
            "name",
            "categories",
            "lat",
            "lng",
            "rating",
            "reviewCount",
            "shortAddress",
            "openingPeriods"
    );

    public AiItineraryRequest create(
            ItineraryGenerationEntity generation,
            TripPlanningProfileEntity profile,
            List<PlaceCandidateEntity> candidates
    ) {
        TripEntity trip = generation.getTrip();
        return new AiItineraryRequest(
                generation.getId().toString(),
                trip.getId().toString(),
                destination(trip),
                trip.getStartDate(),
                trip.getEndDate(),
                companion(profile),
                budget(profile),
                profile.getTravelPace().name(),
                profile.getInterests().stream().map(TripInterest::name).toList(),
                transportation(profile),
                accommodation(profile),
                mustVisitPlaces(profile),
                profile.getAvoidConditions().stream().map(AvoidCondition::name).toList(),
                profile.getFreeRequest(),
                planningRules(),
                candidateTable(candidates)
        );
    }

    private AiItineraryRequest.Destination destination(TripEntity trip) {
        return new AiItineraryRequest.Destination(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    private AiItineraryRequest.Companion companion(TripPlanningProfileEntity profile) {
        return new AiItineraryRequest.Companion(
                profile.getCompanionCount(),
                profile.getCompanionType().name(),
                profile.isHasChildren(),
                profile.getChildCount(),
                profile.getChildAgeGroup() == null ? null : profile.getChildAgeGroup().name(),
                profile.isHasSeniors(),
                profile.getSeniorCount()
        );
    }

    private AiItineraryRequest.Budget budget(TripPlanningProfileEntity profile) {
        return new AiItineraryRequest.Budget(
                profile.getCurrencyCode().name(),
                profile.getBudgetAmount(),
                profile.getBudgetLevel().name(),
                profile.getIncludedBudgetItems().stream().map(BudgetItem::name).toList()
        );
    }

    private AiItineraryRequest.Transportation transportation(TripPlanningProfileEntity profile) {
        return new AiItineraryRequest.Transportation(
                profile.getPrimaryTransportMode().name(),
                profile.getSecondaryTransportModes().stream().map(TransportMode::name).toList()
        );
    }

    private AiItineraryRequest.Accommodation accommodation(TripPlanningProfileEntity profile) {
        return new AiItineraryRequest.Accommodation(
                profile.getAccommodationMode().name(),
                profile.getAccommodationArea() == null ? null : profile.getAccommodationArea().name(),
                profile.getAccommodationName(),
                profile.getCheckInTime(),
                profile.getCheckOutTime()
        );
    }

    private List<AiItineraryRequest.MustVisitPlace> mustVisitPlaces(TripPlanningProfileEntity profile) {
        return profile.getMustVisitPlaces()
                .stream()
                .filter(MustVisitPlaceSnapshot::isResolved)
                .map(this::mustVisitPlace)
                .toList();
    }

    private AiItineraryRequest.MustVisitPlace mustVisitPlace(MustVisitPlaceSnapshot place) {
        return new AiItineraryRequest.MustVisitPlace(
                place.placeId(),
                place.name(),
                place.formattedAddress(),
                place.latitude(),
                place.longitude()
        );
    }

    private AiItineraryRequest.CandidateTable candidateTable(List<PlaceCandidateEntity> candidates) {
        return new AiItineraryRequest.CandidateTable(
                CANDIDATE_FIELDS,
                candidates.stream().map(this::candidateRow).toList()
        );
    }

    private List<Object> candidateRow(PlaceCandidateEntity candidate) {
        return Arrays.asList(
                candidate.getPlaceId(),
                candidate.getName(),
                candidate.getSourceCategories().stream().map(CandidateSearchCategory::name).toList(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getRating(),
                candidate.getUserRatingCount(),
                shortAddress(candidate.getAddress()),
                candidate.getOpeningPeriods()
        );
    }

    private String shortAddress(String address) {
        if (address == null || address.length() <= 80) {
            return address;
        }
        return address.substring(0, 80);
    }

    private List<String> planningRules() {
        return List.of(
                "Use only placeId values included in candidateTable.",
                "Include all mustVisitPlaces in the itinerary unless impossible due to trip duration or opening hours.",
                "mustVisitPlaces are user-selected required places and are also included in candidateTable. Use the same placeId, and use candidateTable.name as the response placeName.",
                "Every itinerary item must include placeId and placeName.",
                "placeName must exactly match the candidate name.",
                "Do not invent places outside the candidate list.",
                "Respect trip dates, travel pace, companions, budget level, transport modes, avoid conditions, and opening hours when provided.",
                "Return only JSON matching AiItineraryResponse without Markdown code fences."
        );
    }
}
