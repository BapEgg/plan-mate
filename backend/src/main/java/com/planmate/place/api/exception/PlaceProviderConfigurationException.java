package com.planmate.place.api.exception;

public class PlaceProviderConfigurationException extends PlaceApiException {

    private static final int STATUS_CODE = 500;
    private static final String CODE = "PLACE_PROVIDER_CONFIGURATION_ERROR";
    private static final String MESSAGE = "External place service is not configured correctly.";

    public PlaceProviderConfigurationException() {
        super(STATUS_CODE, CODE, MESSAGE);
    }
}
