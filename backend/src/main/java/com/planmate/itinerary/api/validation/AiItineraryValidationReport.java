package com.planmate.itinerary.api.validation;

import java.util.List;

public record AiItineraryValidationReport(
        List<ValidationIssue> errors,
        List<ValidationIssue> warnings,
        List<ValidationIssue> unverifiedConditions
) {

    public AiItineraryValidationReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        unverifiedConditions = unverifiedConditions == null
                ? List.of()
                : List.copyOf(unverifiedConditions);
    }

    public static AiItineraryValidationReport empty() {
        return new AiItineraryValidationReport(
                List.of(),
                List.of(),
                List.of()
        );
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean canPersist() {
        return errors.isEmpty();
    }
}
