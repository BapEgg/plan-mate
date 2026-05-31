package com.planmate.common.exception;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of());
    }

    public static ApiErrorResponse validation(List<FieldErrorResponse> fieldErrors) {
        return new ApiErrorResponse("VALIDATION_ERROR", "Invalid request.", fieldErrors);
    }

}
