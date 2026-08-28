package com.planmate.trip.api;

import java.util.Optional;

public interface TripPlanningSnapshotReader {

    Optional<TripPlanningSnapshot> findByTripId(Long tripId);
}
