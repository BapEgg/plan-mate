package com.planmate.trip.api;

public interface TripAccessChecker {

    void checkAccessible(Long userId, Long tripId);
}
