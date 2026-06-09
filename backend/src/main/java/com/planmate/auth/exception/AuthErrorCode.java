package com.planmate.auth.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "Login id already exists."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already exists."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login id or password."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Email is not verified."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Invalid token."),
    EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "EXPIRED_TOKEN", "Expired token."),
    TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "TOKEN_ALREADY_USED", "Token is already used."),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_SEND_FAILED", "Email delivery service is unavailable."),
    REFRESH_TOKEN_STORE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "REFRESH_TOKEN_STORE_UNAVAILABLE",
            "Refresh token store is unavailable."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

}
