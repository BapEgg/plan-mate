package com.planmate.trip.service;

import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPlanningSnapshotQueryService implements TripAccessChecker, TripPlanningSnapshotReader {

    private final TripRepository tripRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;

    public TripPlanningSnapshotQueryService(
            TripRepository tripRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository
    ) {
        this.tripRepository = tripRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkAccessible(Long userId, Long tripId) {
        tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripPlanningSnapshot> findByTripId(Long tripId) {
        return tripPlanningProfileRepository.findWithTripByTrip_Id(tripId)
                .map(this::toSnapshot);
    }

    private TripPlanningSnapshot toSnapshot(TripPlanningProfileEntity profile) {
        TripEntity trip = profile.getTrip();
        return new TripPlanningSnapshot(
                trip.getId(),
                trip.getStartDate(),
                trip.getEndDate(),
                toDestination(trip),
                toCompanion(profile),
                toBudget(profile),
                toPreference(profile),
                toTransportation(profile),
                toAccommodation(profile),
                profile.getDailyStartTime(),
                profile.getDailyEndTime(),
                mustVisitPlaces(profile.getMustVisitPlaces()),
                names(profile.getAvoidConditions()),
                profile.getFreeRequest()
        );
    }

    private TripPlanningSnapshot.Destination toDestination(TripEntity trip) {
        return new TripPlanningSnapshot.Destination(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                toViewport(trip),
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    private TripPlanningSnapshot.Viewport toViewport(TripEntity trip) {
        if (trip.getDestinationViewportLowLatitude() == null
                || trip.getDestinationViewportLowLongitude() == null
                || trip.getDestinationViewportHighLatitude() == null
                || trip.getDestinationViewportHighLongitude() == null) {
            return null;
        }
        return new TripPlanningSnapshot.Viewport(
                trip.getDestinationViewportLowLatitude(),
                trip.getDestinationViewportLowLongitude(),
                trip.getDestinationViewportHighLatitude(),
                trip.getDestinationViewportHighLongitude()
        );
    }

    private TripPlanningSnapshot.Companion toCompanion(TripPlanningProfileEntity profile) {
        return new TripPlanningSnapshot.Companion(
                profile.getCompanionCount(),
                name(profile.getCompanionType()),
                profile.isHasChildren(),
                profile.getChildCount(),
                name(profile.getChildAgeGroup()),
                profile.isHasSeniors(),
                profile.getSeniorCount()
        );
    }

    private TripPlanningSnapshot.Budget toBudget(TripPlanningProfileEntity profile) {
        return new TripPlanningSnapshot.Budget(
                name(profile.getCurrencyCode()),
                profile.getBudgetAmount(),
                name(profile.getBudgetLevel()),
                names(profile.getIncludedBudgetItems())
        );
    }

    private TripPlanningSnapshot.Preference toPreference(TripPlanningProfileEntity profile) {
        return new TripPlanningSnapshot.Preference(
                name(profile.getTravelPace()),
                names(profile.getInterests())
        );
    }

    private TripPlanningSnapshot.Transportation toTransportation(TripPlanningProfileEntity profile) {
        return new TripPlanningSnapshot.Transportation(
                name(profile.getPrimaryTransportMode()),
                names(profile.getSecondaryTransportModes())
        );
    }

    private TripPlanningSnapshot.Accommodation toAccommodation(TripPlanningProfileEntity profile) {
        return new TripPlanningSnapshot.Accommodation(
                name(profile.getAccommodationMode()),
                name(profile.getAccommodationArea()),
                profile.getAccommodationPlaceId(),
                profile.getAccommodationName(),
                profile.getAccommodationFormattedAddress(),
                profile.getAccommodationLatitude(),
                profile.getAccommodationLongitude(),
                profile.getAccommodationTypes(),
                profile.getAccommodationPrimaryType(),
                profile.getCheckInTime(),
                profile.getCheckOutTime()
        );
    }

    private TripPlanningSnapshot.MustVisitPlace toMustVisitPlace(MustVisitPlaceSnapshot place) {
        return new TripPlanningSnapshot.MustVisitPlace(
                place.placeId(),
                place.name(),
                place.formattedAddress(),
                place.latitude(),
                place.longitude(),
                place.types(),
                place.primaryType()
        );
    }

    private List<TripPlanningSnapshot.MustVisitPlace> mustVisitPlaces(List<MustVisitPlaceSnapshot> places) {
        return places == null
                ? List.of()
                : places.stream()
                        .map(this::toMustVisitPlace)
                        .toList();
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private List<String> names(List<? extends Enum<?>> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .map(this::name)
                        .toList();
    }
}
