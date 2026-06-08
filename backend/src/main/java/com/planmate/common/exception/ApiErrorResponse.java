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

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return of(errorCode.code(), errorCode.message());
    }

    public static ApiErrorResponse validation(List<FieldErrorResponse> fieldErrors) {
        return new ApiErrorResponse(
                CommonErrorCode.VALIDATION_ERROR.code(),
                CommonErrorCode.VALIDATION_ERROR.message(),
                fieldErrors
        );
    }

}
