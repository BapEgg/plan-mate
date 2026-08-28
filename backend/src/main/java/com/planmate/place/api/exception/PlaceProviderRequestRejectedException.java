package com.planmate.place.api.exception;

public class PlaceProviderRequestRejectedException extends PlaceApiException {

    private static final int STATUS_CODE = 500;
    private static final String CODE = "PLACE_PROVIDER_REQUEST_REJECTED";
    private static final String MESSAGE = "External place service rejected the application request.";

    public PlaceProviderRequestRejectedException(Throwable cause) {
        super(STATUS_CODE, CODE, MESSAGE, cause);
    }
}
