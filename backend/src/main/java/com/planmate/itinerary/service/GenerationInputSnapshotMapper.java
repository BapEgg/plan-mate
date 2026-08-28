package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.trip.api.TripPlanningSnapshot;
import org.springframework.stereotype.Component;

@Component
public class GenerationInputSnapshotMapper {

    public GenerationInputSnapshot map(TripPlanningSnapshot snapshot) {
        return new GenerationInputSnapshot(
                snapshot.tripId(),
                snapshot.startDate(),
                snapshot.endDate(),
                destination(snapshot.destination()),
                companion(snapshot.companion()),
                budget(snapshot.budget()),
                preference(snapshot.preference()),
                transportation(snapshot.transportation()),
                accommodation(snapshot.accommodation()),
                snapshot.dailyStartTime(),
                snapshot.dailyEndTime(),
                snapshot.mustVisitPlaces()
                        .stream()
                        .map(this::mustVisitPlace)
                        .toList(),
                snapshot.avoidConditions(),
                snapshot.freeRequest()
        );
    }

    private GenerationInputSnapshot.Destination destination(TripPlanningSnapshot.Destination destination) {
        return new GenerationInputSnapshot.Destination(
                destination.placeId(),
                destination.displayName(),
                destination.formattedAddress(),
                destination.latitude(),
                destination.longitude(),
                viewport(destination.viewport()),
                destination.types(),
                destination.primaryType()
        );
    }

    private GenerationInputSnapshot.Viewport viewport(TripPlanningSnapshot.Viewport viewport) {
        if (viewport == null) {
            return null;
        }
        return new GenerationInputSnapshot.Viewport(
                viewport.lowLatitude(),
                viewport.lowLongitude(),
                viewport.highLatitude(),
                viewport.highLongitude()
        );
    }

    private GenerationInputSnapshot.Companion companion(TripPlanningSnapshot.Companion companion) {
        return new GenerationInputSnapshot.Companion(
                companion.companionCount(),
                companion.companionType(),
                companion.hasChildren(),
                companion.childCount(),
                companion.childAgeGroup(),
                companion.hasSeniors(),
                companion.seniorCount()
        );
    }

    private GenerationInputSnapshot.Budget budget(TripPlanningSnapshot.Budget budget) {
        return new GenerationInputSnapshot.Budget(
                budget.currencyCode(),
                budget.amount(),
                budget.level(),
                budget.includedItems()
        );
    }

    private GenerationInputSnapshot.Preference preference(TripPlanningSnapshot.Preference preference) {
        return new GenerationInputSnapshot.Preference(
                preference.travelPace(),
                preference.interests()
        );
    }

    private GenerationInputSnapshot.Transportation transportation(TripPlanningSnapshot.Transportation transportation) {
        return new GenerationInputSnapshot.Transportation(
                transportation.primaryMode(),
                transportation.secondaryModes()
        );
    }

    private GenerationInputSnapshot.Accommodation accommodation(TripPlanningSnapshot.Accommodation accommodation) {
        return new GenerationInputSnapshot.Accommodation(
                accommodation.accommodationMode(),
                accommodation.preferredArea(),
                accommodation.placeId(),
                accommodation.name(),
                accommodation.formattedAddress(),
                accommodation.latitude(),
                accommodation.longitude(),
                accommodation.types(),
                accommodation.primaryType(),
                accommodation.checkInTime(),
                accommodation.checkOutTime()
        );
    }

    private GenerationInputSnapshot.MustVisitPlace mustVisitPlace(TripPlanningSnapshot.MustVisitPlace place) {
        return new GenerationInputSnapshot.MustVisitPlace(
                place.placeId(),
                place.name(),
                place.formattedAddress(),
                place.latitude(),
                place.longitude(),
                place.types(),
                place.primaryType()
        );
    }
}
