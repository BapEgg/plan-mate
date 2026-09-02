package com.planmate.regeneration.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.regeneration.dto.CreateItineraryRegenerationRequest;
import com.planmate.regeneration.dto.ItineraryRegenerationResponse;
import com.planmate.regeneration.service.ItineraryRegenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-regenerations")
public class ItineraryRegenerationController {

    private final ItineraryRegenerationService service;

    public ItineraryRegenerationController(ItineraryRegenerationService service) {
        this.service = service;
    }

    @PostMapping
    public ItineraryRegenerationResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @Valid @RequestBody CreateItineraryRegenerationRequest request
    ) {
        return service.create(user.userId(), tripId, request);
    }

    @GetMapping("/latest")
    public ResponseEntity<ItineraryRegenerationResponse> latest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return service.latest(user.userId(), tripId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{regenerationId}")
    public ItineraryRegenerationResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long regenerationId
    ) {
        return service.get(user.userId(), tripId, regenerationId);
    }

    @PostMapping("/{regenerationId}/apply")
    public ItineraryRegenerationResponse apply(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long regenerationId
    ) {
        return service.apply(user.userId(), tripId, regenerationId);
    }

    @PostMapping("/{regenerationId}/reject")
    public ItineraryRegenerationResponse reject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long regenerationId
    ) {
        return service.reject(user.userId(), tripId, regenerationId);
    }
}
