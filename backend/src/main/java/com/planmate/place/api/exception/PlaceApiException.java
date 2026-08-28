package com.planmate.place.api.exception;

public abstract class PlaceApiException extends RuntimeException {

    private final int statusCode;
    private final String code;

    protected PlaceApiException(int statusCode, String code, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    protected PlaceApiException(int statusCode, String code, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
    }

    public int statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }
}
