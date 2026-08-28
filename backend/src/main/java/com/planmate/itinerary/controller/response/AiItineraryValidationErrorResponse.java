package com.planmate.itinerary.controller.response;

import com.planmate.common.exception.FieldErrorResponse;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import java.util.List;
import java.util.Objects;

public record AiItineraryValidationErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        AiItineraryValidationReport validationReport
) {

    public AiItineraryValidationErrorResponse {
        fieldErrors = fieldErrors == null
                ? List.of()
                : List.copyOf(fieldErrors);
        Objects.requireNonNull(validationReport);
    }
}
