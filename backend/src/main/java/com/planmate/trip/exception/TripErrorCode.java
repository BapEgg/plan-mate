package com.planmate.trip.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {

    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip not found."),
    INVALID_TRIP_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_TRIP_REQUEST", "Invalid trip request.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TripErrorCode(HttpStatus status, String code, String message) {
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
