package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryRequestFactory {

    public AiItineraryRequest create(
            ItineraryGenerationEntity generation,
            TripPlanningProfileEntity profile
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
                planningRules()
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

    private List<String> planningRules() {
        return List.of(
                "Markdown 코드 블록이나 설명 없이 GroundedItineraryDraft 형식의 JSON만 반환한다.",
                "모든 일정 항목은 sequence, placeId, startTime, durationMinutes를 포함해야 한다.",
                "응답에는 placeName, address, latitude, longitude, rating, routeInfo, sourceLinks, 추천 이유를 포함하지 않는다.",
                "여행 일수와 같은 개수의 day 항목을 만들고, day 번호는 1부터 시작한다.",
                "여행 기간상 불가능한 경우가 아니라면 mustVisitPlaces를 모두 포함한다.",
                "여행 날짜, 여행 속도, 동행자, 예산 수준, 이동수단, 회피 조건, freeRequest를 반영한다."
        );
    }
}
