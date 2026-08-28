package com.planmate.place.api.exception;

public class InvalidPlaceIdException extends PlaceApiException {

    private static final int STATUS_CODE = 400;
    private static final String CODE = "INVALID_PLACE_ID";
    private static final String MESSAGE = "Invalid destination place id.";

    public InvalidPlaceIdException() {
        super(STATUS_CODE, CODE, MESSAGE);
    }
}
