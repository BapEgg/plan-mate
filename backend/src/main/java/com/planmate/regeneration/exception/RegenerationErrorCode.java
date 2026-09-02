package com.planmate.regeneration.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RegenerationErrorCode implements ErrorCode {
    REGENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGENERATION_NOT_FOUND", "Itinerary regeneration not found."),
    REGENERATION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "REGENERATION_ALREADY_ACTIVE", "Another itinerary regeneration is already active."),
    REGENERATION_NOT_READY(HttpStatus.CONFLICT, "REGENERATION_NOT_READY", "The regenerated itinerary is not ready for review."),
    REGENERATION_STALE_BASE(HttpStatus.CONFLICT, "REGENERATION_STALE_BASE", "The itinerary changed after regeneration started."),
    REGENERATION_INVALID_RANGE(HttpStatus.BAD_REQUEST, "REGENERATION_INVALID_RANGE", "The selected itinerary range is invalid."),
    REGENERATION_NO_REPLACEMENT(HttpStatus.BAD_REQUEST, "REGENERATION_NO_REPLACEMENT", "At least one selected item must be replaced."),
    REGENERATION_FIXED_ITEM_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "REGENERATION_FIXED_ITEM_CONFLICT", "A fixed itinerary item could not be preserved."),
    REGENERATION_WINDOW_CLOSED(HttpStatus.CONFLICT, "REGENERATION_WINDOW_CLOSED", "This part of the itinerary can no longer be regenerated.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    RegenerationErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
