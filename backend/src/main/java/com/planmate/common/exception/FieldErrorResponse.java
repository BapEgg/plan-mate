package com.planmate.common.exception;

public record FieldErrorResponse(
        String field,
        String message
) {
}
