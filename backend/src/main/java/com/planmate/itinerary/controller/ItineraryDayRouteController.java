package com.planmate.itinerary.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.dto.ItineraryDayRouteResponse;
import com.planmate.itinerary.service.ItineraryDayRouteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/routes/days")
public class ItineraryDayRouteController {

    private final ItineraryDayRouteService routeService;

    public ItineraryDayRouteController(ItineraryDayRouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping("/{dayNumber}")
    public ItineraryDayRouteResponse getDayRoute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable int dayNumber
    ) {
        return routeService.getDayRoute(user.userId(), tripId, dayNumber);
    }
}
