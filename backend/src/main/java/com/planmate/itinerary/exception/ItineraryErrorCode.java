package com.planmate.itinerary.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ItineraryErrorCode implements ErrorCode {

    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GENERATION_NOT_FOUND", "Itinerary generation not found."),
    GENERATION_NOT_READY(HttpStatus.CONFLICT, "GENERATION_NOT_READY", "Itinerary generation is not ready for planning."),
    GENERATION_INPUT_NOT_FOUND(HttpStatus.CONFLICT, "GENERATION_INPUT_NOT_FOUND", "Itinerary generation input snapshot not found."),
    GENERATION_CANDIDATES_NOT_FOUND(HttpStatus.CONFLICT, "GENERATION_CANDIDATES_NOT_FOUND", "Itinerary generation candidate snapshots not found."),
    GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT(
            HttpStatus.CONFLICT,
            "GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT",
            "The generation has already been completed with a different itinerary draft."
    ),
    GENERATION_ITINERARY_STATE_INCONSISTENT(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "GENERATION_ITINERARY_STATE_INCONSISTENT",
            "Itinerary generation and persisted itinerary state are inconsistent."
    ),
    GENERATION_TIME_WINDOW_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "GENERATION_TIME_WINDOW_INVALID",
            "The itinerary generation contains an invalid daily time window."
    ),
    NO_RECOMMENDATION_CANDIDATES(HttpStatus.CONFLICT, "NO_RECOMMENDATION_CANDIDATES", "No usable recommendation candidates were found."),
    UNSUPPORTED_PROMPT_VERSION(HttpStatus.CONFLICT, "UNSUPPORTED_PROMPT_VERSION", "Itinerary prompt version is not supported."),
    INVALID_AI_RESPONSE(HttpStatus.BAD_REQUEST, "INVALID_AI_RESPONSE", "Invalid itinerary response."),
    AI_RESPONSE_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "AI_RESPONSE_VALIDATION_FAILED", "AI itinerary draft validation failed."),
    PLANNING_PROFILE_NOT_FOUND(HttpStatus.CONFLICT, "PLANNING_PROFILE_NOT_FOUND", "Trip planning profile not found."),
    DESTINATION_NOT_RESOLVED(HttpStatus.CONFLICT, "DESTINATION_NOT_RESOLVED", "Trip destination has not been resolved."),
    ITINERARY_DAY_NOT_FOUND(HttpStatus.NOT_FOUND, "ITINERARY_DAY_NOT_FOUND", "Itinerary day not found."),
    ROUTE_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ROUTE_QUOTA_EXCEEDED", "Daily route lookup limit has been reached."),
    ROUTE_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "ROUTE_PROVIDER_TIMEOUT", "Route provider request timed out."),
    ROUTE_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ROUTE_PROVIDER_UNAVAILABLE", "Route provider is unavailable."),
    ROUTE_PROVIDER_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ROUTE_PROVIDER_REQUEST_FAILED", "Route provider request failed."),
    ROUTE_TRANSPORT_MODE_UNSUPPORTED(HttpStatus.INTERNAL_SERVER_ERROR, "ROUTE_TRANSPORT_MODE_UNSUPPORTED", "Route transport mode is unsupported."),
    GENERATION_CANDIDATE_LOCATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "GENERATION_CANDIDATE_LOCATION_INVALID", "Itinerary generation candidate location is invalid.");

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
