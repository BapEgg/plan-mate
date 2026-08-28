package com.planmate.itinerary.api;

import java.util.Optional;

public interface LatestItineraryReader {

    Optional<ItineraryReadModel> findLatestByTripId(Long tripId);
}
