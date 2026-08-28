package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class AiItineraryValidationReportBuilder {

    private final List<ValidationIssue> errors = new ArrayList<>();
    private final List<ValidationIssue> warnings = new ArrayList<>();
    private final List<ValidationIssue> unverifiedConditions = new ArrayList<>();

    void error(
            ValidationIssueCode code,
            String path,
            Integer day,
            Integer sequence,
            String placeId
    ) {
        errors.add(ValidationIssue.of(code, path, day, sequence, placeId));
    }

    void error(ValidationIssue issue) {
        errors.add(Objects.requireNonNull(issue));
    }

    void errors(List<ValidationIssue> issues) {
        if (issues != null) {
            issues.forEach(this::error);
        }
    }

    void warning(
            ValidationIssue issue
    ) {
        warnings.add(issue);
    }

    void unverifiedCondition(
            ValidationIssue issue
    ) {
        unverifiedConditions.add(issue);
    }

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    AiItineraryValidationReport build() {
        return new AiItineraryValidationReport(
                errors,
                warnings,
                unverifiedConditions
        );
    }
}
