package com.planmate.itinerary.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.service.ItineraryGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-generations")
public class ItineraryGenerationController {

    private final ItineraryGenerationService generationService;

    public ItineraryGenerationController(ItineraryGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping
    public ItineraryGenerationCreateResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return generationService.create(user.userId(), tripId);
    }

    @GetMapping("/{generationId}")
    public ItineraryGenerationDetailResponse getDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long generationId
    ) {
        return generationService.getDetail(user.userId(), tripId, generationId);
    }

    @GetMapping("/latest")
    public ResponseEntity<ItineraryGenerationDetailResponse> getLatest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return generationService.getLatest(user.userId(), tripId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
