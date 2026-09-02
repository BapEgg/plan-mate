package com.planmate.itinerary.route;

import java.time.Instant;
import java.util.List;

public record RoutePath(
        String provider,
        int distanceMeters,
        int durationSeconds,
        List<RouteCoordinate> geometry,
        Instant verifiedAt
) {
    public RoutePath {
        geometry = List.copyOf(geometry);
    }
}
