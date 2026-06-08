package com.planmate.auth.exception;

public class TokenAlreadyUsedException extends RuntimeException {

    public TokenAlreadyUsedException() {
        super("Token is already used.");
    }

}
