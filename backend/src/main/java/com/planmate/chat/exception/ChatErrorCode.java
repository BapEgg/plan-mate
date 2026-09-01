package com.planmate.chat.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements ErrorCode {

    INVALID_MESSAGE_BODY(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_BODY", "Message body must be between 1 and 2000 characters."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "No message found for that client message id.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ChatErrorCode(HttpStatus status, String code, String message) {
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
