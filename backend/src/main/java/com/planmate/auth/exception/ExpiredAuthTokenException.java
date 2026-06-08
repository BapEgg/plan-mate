package com.planmate.auth.exception;

public class ExpiredAuthTokenException extends RuntimeException {

    public ExpiredAuthTokenException() {
        super("Expired token.");
    }

}
