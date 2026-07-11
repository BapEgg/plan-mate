package com.planmate.itinerary.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.dto.ItineraryPlaceView;
import com.planmate.itinerary.service.ItineraryPlaceViewService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-place-views")
public class ItineraryPlaceViewController {

    private final ItineraryPlaceViewService itineraryPlaceViewService;

    public ItineraryPlaceViewController(ItineraryPlaceViewService itineraryPlaceViewService) {
        this.itineraryPlaceViewService = itineraryPlaceViewService;
    }

    @GetMapping
    public List<ItineraryPlaceView> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @RequestParam(required = false) Integer dayNo
    ) {
        return itineraryPlaceViewService.listLatestItineraryPlaceViews(user.userId(), tripId, dayNo);
    }
}
