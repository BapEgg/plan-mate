package com.planmate.itinerary.dto;

import com.planmate.itinerary.route.RouteCoordinate;
import java.time.Instant;
import java.util.List;

public record ItineraryDayRouteResponse(
        Long itineraryId,
        int itineraryVersion,
        int dayNumber,
        String provider,
        String status,
        List<Leg> legs
) {
    public ItineraryDayRouteResponse {
        legs = List.copyOf(legs);
    }

    public record Leg(
            Long fromItemId,
            Long toItemId,
            int sequence,
            String status,
            Integer distanceMeters,
            Integer durationSeconds,
            List<RouteCoordinate> geometry,
            Instant verifiedAt
    ) {
        public Leg {
            geometry = List.copyOf(geometry);
        }

        public static Leg unresolved(Long fromItemId, Long toItemId, int sequence) {
            return new Leg(fromItemId, toItemId, sequence, "LOCATION_UNRESOLVED", null, null, List.of(), null);
        }

        public static Leg notFound(Long fromItemId, Long toItemId, int sequence) {
            return new Leg(fromItemId, toItemId, sequence, "ROUTE_NOT_FOUND", null, null, List.of(), null);
        }
    }
}
