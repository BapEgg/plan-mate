package com.planmate.itinerary.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ItineraryErrorCode implements ErrorCode {

    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GENERATION_NOT_FOUND", "Itinerary generation not found."),
    GENERATION_NOT_READY(HttpStatus.CONFLICT, "GENERATION_NOT_READY", "Itinerary generation is not ready for planning."),
    INVALID_AI_RESPONSE(HttpStatus.BAD_REQUEST, "INVALID_AI_RESPONSE", "Invalid itinerary response."),
    PLANNING_PROFILE_NOT_FOUND(HttpStatus.CONFLICT, "PLANNING_PROFILE_NOT_FOUND", "Trip planning profile not found."),
    DESTINATION_NOT_RESOLVED(HttpStatus.CONFLICT, "DESTINATION_NOT_RESOLVED", "Trip destination has not been resolved.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ItineraryErrorCode(HttpStatus status, String code, String message) {
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
