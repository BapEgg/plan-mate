package com.planmate.itinerary.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.service.AiItineraryRequestService;
import com.planmate.itinerary.service.ItineraryGenerationService;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ManualItineraryGenerationControllerTest {

    private final ItineraryGenerationService generationService = Mockito.mock(ItineraryGenerationService.class);
    private final AiItineraryRequestService aiItineraryRequestService = Mockito.mock(AiItineraryRequestService.class);
    private final ManualItineraryResponseService manualItineraryResponseService = Mockito.mock(ManualItineraryResponseService.class);
    private final ManualItineraryGenerationController controller = new ManualItineraryGenerationController(
            generationService,
            aiItineraryRequestService,
            manualItineraryResponseService
    );

    @Test
    void validateManualResponseReturnsValidationReportWithoutSubmitting() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(new ItineraryDraftDay(
                        1,
                        List.of(new ItineraryDraftItem(1, "place-1", "09:00", 60))
                ))
        );
        AiItineraryValidationReport report = AiItineraryValidationReport.empty();
        given(manualItineraryResponseService.validate(99L, 1L, 10L, draft))
                .willReturn(report);

        AiItineraryValidationReport result = controller.validateManualResponse(
                new AuthenticatedUser(99L, "USER"),
                1L,
                10L,
                draft
        );

        assertThat(result).isSameAs(report);
        verify(manualItineraryResponseService).validate(99L, 1L, 10L, draft);
    }
}
