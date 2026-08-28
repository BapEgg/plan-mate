package com.planmate.trip.api;

public interface TripDetailTripReader {

    TripDetailTrip getAccessibleTrip(Long userId, Long tripId);
}
