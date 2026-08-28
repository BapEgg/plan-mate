package com.planmate.trip.api;

public interface TripMembershipChecker {

    boolean isMember(Long userId, Long tripId);
}
