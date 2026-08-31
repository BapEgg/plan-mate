package com.planmate.itinerary.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.service.AiItineraryRequestService;
import com.planmate.itinerary.service.ItineraryGenerationService;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.itinerary.manual-handoff-enabled", havingValue = "true")
@RequestMapping("/api/trips/{tripId}/itinerary-generations")
public class ManualItineraryGenerationController {

    private final ItineraryGenerationService generationService;
    private final AiItineraryRequestService aiItineraryRequestService;
    private final ManualItineraryResponseService manualItineraryResponseService;

    public ManualItineraryGenerationController(
            ItineraryGenerationService generationService,
            AiItineraryRequestService aiItineraryRequestService,
            ManualItineraryResponseService manualItineraryResponseService
    ) {
        this.generationService = generationService;
        this.aiItineraryRequestService = aiItineraryRequestService;
        this.manualItineraryResponseService = manualItineraryResponseService;
    }

    @GetMapping(value = "/{generationId}/manual-prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getManualPrompt(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long generationId
    ) {
        return aiItineraryRequestService.getPrompt(user.userId(), tripId, generationId);
    }

    @GetMapping("/{generationId}/ai-request")
    public AiItineraryRequest getAiRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long generationId
    ) {
        return aiItineraryRequestService.getRequest(user.userId(), tripId, generationId);
    }

    @PostMapping("/{generationId}/manual-response")
    public ItineraryGenerationDetailResponse submitManualResponse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long generationId,
            @Valid @RequestBody AiItineraryDraft response
    ) {
        manualItineraryResponseService.submit(user.userId(), tripId, generationId, response);
        return generationService.getDetail(user.userId(), tripId, generationId);
    }

    @PostMapping("/{generationId}/manual-response/validate")
    public AiItineraryValidationReport validateManualResponse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long tripId,
            @PathVariable Long generationId,
            @Valid @RequestBody AiItineraryDraft response
    ) {
        return manualItineraryResponseService.validate(user.userId(), tripId, generationId, response);
    }
}
