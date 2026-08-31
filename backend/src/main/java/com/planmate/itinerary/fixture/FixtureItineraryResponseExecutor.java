package com.planmate.itinerary.fixture;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("itinerary-fixture")
@ConditionalOnProperty(
        prefix = "app.itinerary.fixture-response",
        name = "enabled",
        havingValue = "true"
)
public class FixtureItineraryResponseExecutor {

    private final ManualItineraryResponseService responseService;

    public FixtureItineraryResponseExecutor(ManualItineraryResponseService responseService) {
        this.responseService = responseService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submit(Long tripId, Long generationId, AiItineraryDraft draft) {
        responseService.submitProviderResponse(tripId, generationId, draft);
    }
}
