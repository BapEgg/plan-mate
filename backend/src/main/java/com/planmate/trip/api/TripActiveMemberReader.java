package com.planmate.trip.api;

import java.util.List;

/**
 * Exposes the active member identities needed by consumers without leaking trip persistence types.
 */
public interface TripActiveMemberReader {

    List<Long> activeMemberIds(Long tripId);
}
