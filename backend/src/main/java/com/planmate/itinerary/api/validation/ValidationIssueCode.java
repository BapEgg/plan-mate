package com.planmate.itinerary.api.validation;

public enum ValidationIssueCode {

    GENERATION_ID_MISMATCH(
            "generationId must match the current itinerary generation."
    ),
    DRAFT_REQUIRED(
            "draft is required."
    ),
    DAYS_REQUIRED(
            "days are required."
    ),
    DAY_COUNT_MISMATCH(
            "days size must match the trip day count."
    ),
    DAY_OUT_OF_RANGE(
            "day must be within the trip day range."
    ),
    DUPLICATE_DAY(
            "day must not be duplicated."
    ),
    DAY_ITEMS_REQUIRED(
            "day items are required."
    ),
    ITEM_REQUIRED(
            "item is required."
    ),
    INVALID_SEQUENCE(
            "sequence must be positive."
    ),
    DUPLICATE_SEQUENCE(
            "sequence must not be duplicated within a day."
    ),
    PLACE_ID_REQUIRED(
            "placeId is required."
    ),
    INVALID_START_TIME(
            "startTime must use HH:mm format."
    ),
    INVALID_DURATION(
            "durationMinutes must be positive."
    ),
    CANDIDATE_NOT_ALLOWED(
            "placeId must be included in the generation candidate whitelist."
    ),
    REQUIRED_PLACE_MISSING(
            "A required place must be included in the itinerary."
    ),
    ITEM_TIME_OVERLAP(
            "Itinerary items must not overlap within the same day."
    ),
    OUTSIDE_DAILY_WINDOW(
            "Itinerary item must stay within the configured daily time window."
    ),
    ITEM_CROSSES_DAY_BOUNDARY(
            "Itinerary item must not continue past the end of the day."
    ),
    REPEATED_PLACE(
            "The same place appears multiple times in the itinerary."
    ),
    AVOID_CONDITION_VIOLATED(
            "The itinerary includes a place that violates an avoid condition."
    ),
    AVOID_CONDITION_NOT_VERIFIED(
            "The avoid condition could not be verified with the available data."
    ),
    ITEM_SEQUENCE_TIME_ORDER_INVALID(
            "Itinerary item sequence must follow chronological order."
    ),
    INSUFFICIENT_TRAVEL_TIME(
            "The available gap is shorter than the required travel time."
    ),
    ADJACENT_ROUTE_NOT_FOUND(
            "No route was found between adjacent itinerary items."
    ),
    ADJACENT_ROUTE_NOT_VERIFIED(
            "Routes between adjacent itinerary items could not be verified."
    );

    private final String defaultMessage;

    ValidationIssueCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
