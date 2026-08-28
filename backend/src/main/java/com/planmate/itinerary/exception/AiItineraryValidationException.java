package com.planmate.itinerary.exception;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import java.util.Objects;

public class AiItineraryValidationException extends ItineraryException {

    private final AiItineraryValidationReport validationReport;

    public AiItineraryValidationException(AiItineraryValidationReport validationReport) {
        super(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED);
        this.validationReport = Objects.requireNonNull(validationReport);
    }

    public AiItineraryValidationReport validationReport() {
        return validationReport;
    }
}
