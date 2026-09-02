package com.planmate.vote.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.vote.dto.CastBallotRequest;
import com.planmate.vote.dto.ItineraryVoteResponse;
import com.planmate.vote.service.ItineraryVoteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-votes")
public class ItineraryVoteController {

    private final ItineraryVoteService voteService;

    public ItineraryVoteController(ItineraryVoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping
    public List<ItineraryVoteResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return voteService.list(user.userId(), tripId);
    }

    @PostMapping("/proposals/{proposalId}")
    public ItineraryVoteResponse open(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long proposalId
    ) {
        return voteService.open(user.userId(), tripId, proposalId);
    }

    @PutMapping("/{voteId}/ballot")
    public ItineraryVoteResponse cast(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long voteId,
            @Valid @RequestBody CastBallotRequest request
    ) {
        return voteService.cast(user.userId(), tripId, voteId, request.choice());
    }

    @DeleteMapping("/{voteId}")
    public ItineraryVoteResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long voteId
    ) {
        return voteService.cancel(user.userId(), tripId, voteId);
    }
}
