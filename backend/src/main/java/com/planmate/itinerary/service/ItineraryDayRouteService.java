package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryDayRouteResponse;
import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.route.RoutePath;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.kakao.KakaoDrivingRouteProvider;
import com.planmate.itinerary.service.ItineraryDayRoutePlanReader.DayRouteItem;
import com.planmate.itinerary.service.ItineraryDayRoutePlanReader.DayRoutePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ItineraryDayRouteService {

    private static final String PROVIDER = "KAKAO";

    private final ItineraryDayRoutePlanReader planReader;
    private final PlaceDisplayResolver placeDisplayResolver;
    private final KakaoDrivingRouteProvider routeProvider;

    public ItineraryDayRouteService(
            ItineraryDayRoutePlanReader planReader,
            PlaceDisplayResolver placeDisplayResolver,
            KakaoDrivingRouteProvider routeProvider
    ) {
        this.planReader = planReader;
        this.placeDisplayResolver = placeDisplayResolver;
        this.routeProvider = routeProvider;
    }

    public ItineraryDayRouteResponse getDayRoute(Long userId, Long tripId, int dayNumber) {
        DayRoutePlan plan = planReader.read(userId, tripId, dayNumber);
        Map<String, ItineraryPlaceDisplayView> displays = placeDisplayResolver.resolveListViews(
                plan.items().stream().map(DayRouteItem::placeId).toList()
        );

        List<ItineraryDayRouteResponse.Leg> legs = new ArrayList<>();
        for (int index = 0; index + 1 < plan.items().size(); index++) {
            DayRouteItem originItem = plan.items().get(index);
            DayRouteItem destinationItem = plan.items().get(index + 1);
            ItineraryPlaceDisplayView origin = displays.get(originItem.placeId());
            ItineraryPlaceDisplayView destination = displays.get(destinationItem.placeId());
            int sequence = index + 1;

            if (!hasLocation(origin) || !hasLocation(destination)) {
                legs.add(ItineraryDayRouteResponse.Leg.unresolved(
                        originItem.itemId(), destinationItem.itemId(), sequence
                ));
                continue;
            }

            Optional<RoutePath> path = routeProvider.findDetailedRoute(
                    new RoutePoint(origin.location().latitude(), origin.location().longitude()),
                    new RoutePoint(destination.location().latitude(), destination.location().longitude())
            );
            if (path.isEmpty()) {
                legs.add(ItineraryDayRouteResponse.Leg.notFound(
                        originItem.itemId(), destinationItem.itemId(), sequence
                ));
                continue;
            }

            RoutePath resolved = path.orElseThrow();
            legs.add(new ItineraryDayRouteResponse.Leg(
                    originItem.itemId(),
                    destinationItem.itemId(),
                    sequence,
                    "READY",
                    resolved.distanceMeters(),
                    resolved.durationSeconds(),
                    resolved.geometry(),
                    resolved.verifiedAt()
            ));
        }

        String status = legs.stream().allMatch(leg -> "READY".equals(leg.status())) ? "READY" : "PARTIAL";
        return new ItineraryDayRouteResponse(
                plan.itineraryId(),
                plan.itineraryVersion(),
                plan.dayNumber(),
                PROVIDER,
                status,
                legs
        );
    }

    private boolean hasLocation(ItineraryPlaceDisplayView display) {
        return display != null && display.resolved() && display.location() != null;
    }
}
