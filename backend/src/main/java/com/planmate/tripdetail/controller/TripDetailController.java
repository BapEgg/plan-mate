package com.planmate.tripdetail.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.tripdetail.application.TripDetailQueryFacade;
import com.planmate.tripdetail.dto.TripDetailResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripDetailController {

    private final TripDetailQueryFacade tripDetailQueryFacade;

    public TripDetailController(TripDetailQueryFacade tripDetailQueryFacade) {
        this.tripDetailQueryFacade = tripDetailQueryFacade;
    }

    @GetMapping("/{tripId}")
    public TripDetailResponse getDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return tripDetailQueryFacade.getDetail(user.userId(), tripId);
    }
}
