package com.planmate.user.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_IMAGE", "Invalid profile image."),
    PROFILE_IMAGE_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PROFILE_IMAGE_SAVE_FAILED",
            "Failed to save profile image."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus status, String code, String message) {
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
