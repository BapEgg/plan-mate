package com.planmate.proposal.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.proposal.dto.CreateItineraryProposalRequest;
import com.planmate.proposal.dto.ItineraryProposalResponse;
import com.planmate.proposal.service.ItineraryProposalService;
import com.planmate.revision.dto.ItineraryRevisionResponse;
import com.planmate.revision.service.ItineraryRevisionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-proposals")
public class ItineraryProposalController {

    private final ItineraryProposalService proposalService;
    private final ItineraryRevisionService revisionService;

    public ItineraryProposalController(
            ItineraryProposalService proposalService,
            ItineraryRevisionService revisionService
    ) {
        this.proposalService = proposalService;
        this.revisionService = revisionService;
    }

    @PostMapping
    public ItineraryProposalResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @Valid @RequestBody CreateItineraryProposalRequest request
    ) {
        return proposalService.create(user.userId(), tripId, request);
    }

    @GetMapping
    public List<ItineraryProposalResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId
    ) {
        return proposalService.list(user.userId(), tripId);
    }

    @PostMapping("/{proposalId}/apply")
    public ItineraryRevisionResponse apply(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long proposalId
    ) {
        var itinerary = revisionService.applyDirect(user.userId(), tripId, proposalId);
        return ItineraryRevisionResponse.from(itinerary, itinerary.getId());
    }
}
