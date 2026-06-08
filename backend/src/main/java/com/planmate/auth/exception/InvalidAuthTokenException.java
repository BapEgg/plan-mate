package com.planmate.auth.exception;

public class InvalidAuthTokenException extends RuntimeException {

    public InvalidAuthTokenException() {
        super("Invalid token.");
    }

}
