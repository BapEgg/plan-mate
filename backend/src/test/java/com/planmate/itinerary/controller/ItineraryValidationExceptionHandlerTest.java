package com.planmate.itinerary.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.controller.response.AiItineraryValidationErrorResponse;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ItineraryValidationExceptionHandlerTest {

    private final ItineraryValidationExceptionHandler handler = new ItineraryValidationExceptionHandler();

    @Test
    void handlesAiItineraryValidationExceptionWithReport() {
        AiItineraryValidationReport report = new AiItineraryValidationReport(
                List.of(ValidationIssue.of(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        "days[0].items[1].placeId",
                        1,
                        2,
                        "invalid-place"
                )),
                List.of(),
                List.of()
        );

        ResponseEntity<AiItineraryValidationErrorResponse> response =
                handler.handle(new AiItineraryValidationException(report));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED.code());
        assertThat(response.getBody().message()).isEqualTo(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED.message());
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().validationReport()).isEqualTo(report);
    }
}
