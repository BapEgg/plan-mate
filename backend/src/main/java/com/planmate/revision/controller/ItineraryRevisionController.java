package com.planmate.revision.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.revision.dto.ItineraryRevisionResponse;
import com.planmate.revision.service.ItineraryRevisionQueryService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-revisions")
public class ItineraryRevisionController {

    private final ItineraryRevisionQueryService queryService;

    public ItineraryRevisionController(ItineraryRevisionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<ItineraryRevisionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return queryService.list(user.userId(), tripId);
    }
}
