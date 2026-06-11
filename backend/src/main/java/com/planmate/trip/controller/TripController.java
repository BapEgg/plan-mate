package com.planmate.trip.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripDetailResponse;
import com.planmate.trip.dto.TripSummaryResponse;
import com.planmate.trip.service.TripService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripSummaryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TripCreateRequest request
    ) {
        TripSummaryResponse response = tripService.create(user.userId(), request);
        return ResponseEntity
                .created(URI.create("/api/trips/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<TripSummaryResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return tripService.listMine(user.userId());
    }

    @GetMapping("/{tripId}")
    public TripDetailResponse getDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return tripService.getDetail(user.userId(), tripId);
    }

}
