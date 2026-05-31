package com.planmate.auth.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email already exists: " + email);
    }

}
