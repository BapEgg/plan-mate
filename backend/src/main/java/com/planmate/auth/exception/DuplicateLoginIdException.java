package com.planmate.auth.exception;

public class DuplicateLoginIdException extends RuntimeException {

    public DuplicateLoginIdException(String loginId) {
        super("Login id already exists: " + loginId);
    }

}
