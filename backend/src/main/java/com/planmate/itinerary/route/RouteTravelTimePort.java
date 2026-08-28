package com.planmate.itinerary.route;

import java.time.Duration;
import java.util.Optional;

public interface RouteTravelTimePort {

    Optional<RouteTravelTime> findRoute(
            RoutePoint origin,
            RoutePoint destination,
            TravelMode travelMode
    );

    enum TravelMode {
        WALK,
        BICYCLE,
        DRIVE
    }

    record RoutePoint(
            double latitude,
            double longitude
    ) {
    }

    record RouteTravelTime(
            Duration duration,
            long distanceMeters
    ) {
    }
}
