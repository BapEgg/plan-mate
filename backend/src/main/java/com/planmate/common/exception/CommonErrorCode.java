package com.planmate.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "This action requires a different role."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "DATA_CONFLICT", "Data conflict."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

}
