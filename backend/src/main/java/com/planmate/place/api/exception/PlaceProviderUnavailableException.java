package com.planmate.place.api.exception;

public class PlaceProviderUnavailableException extends PlaceApiException {

    private static final int STATUS_CODE = 503;
    private static final String CODE = "PLACE_PROVIDER_UNAVAILABLE";
    private static final String MESSAGE = "External place service is unavailable.";

    public PlaceProviderUnavailableException() {
        super(STATUS_CODE, CODE, MESSAGE);
    }

    public PlaceProviderUnavailableException(Throwable cause) {
        super(STATUS_CODE, CODE, MESSAGE, cause);
    }
}
