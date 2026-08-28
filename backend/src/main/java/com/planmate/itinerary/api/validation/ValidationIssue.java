package com.planmate.itinerary.api.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationIssue(
        ValidationIssueCode code,
        String message,
        String path,
        Integer day,
        Integer sequence,
        String placeId,
        String condition,
        List<ValidationTarget> relatedTargets
) {

    public ValidationIssue(
            ValidationIssueCode code,
            String message,
            String path,
            Integer day,
            Integer sequence,
            String placeId,
            List<ValidationTarget> relatedTargets
    ) {
        this(code, message, path, day, sequence, placeId, null, relatedTargets);
    }

    public ValidationIssue {
        Objects.requireNonNull(code);
        message = hasText(message) ? message : code.defaultMessage();
        relatedTargets = relatedTargets == null
                ? List.of()
                : List.copyOf(relatedTargets);
    }

    public static ValidationIssue of(
            ValidationIssueCode code,
            String path,
            Integer day,
            Integer sequence,
            String placeId
    ) {
        return new ValidationIssue(
                code,
                code.defaultMessage(),
                path,
                day,
                sequence,
                placeId,
                null,
                List.of()
        );
    }

    public static ValidationIssue forCondition(
            ValidationIssueCode code,
            String path,
            Integer day,
            Integer sequence,
            String placeId,
            String condition
    ) {
        return new ValidationIssue(
                code,
                code.defaultMessage(),
                path,
                day,
                sequence,
                placeId,
                condition,
                List.of()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
