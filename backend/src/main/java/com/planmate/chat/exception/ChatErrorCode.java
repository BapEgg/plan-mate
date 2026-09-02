package com.planmate.chat.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements ErrorCode {

    INVALID_MESSAGE_BODY(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_BODY", "Message body must be between 1 and 2000 characters."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "No message found for that identifier."),
    INVALID_REPLY_TARGET(HttpStatus.BAD_REQUEST, "INVALID_REPLY_TARGET", "The reply target is not available in this membership interval."),
    MESSAGE_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "MESSAGE_DELETE_FORBIDDEN", "Only the message author can delete this message."),
    MESSAGE_DELETE_WINDOW_EXPIRED(HttpStatus.CONFLICT, "MESSAGE_DELETE_WINDOW_EXPIRED", "Messages can only be deleted within five minutes."),
    MESSAGE_ALREADY_DELETED(HttpStatus.CONFLICT, "MESSAGE_ALREADY_DELETED", "A deleted message cannot be replied to or reacted to."),
    INVALID_REACTION(HttpStatus.BAD_REQUEST, "INVALID_REACTION", "Reaction must be LIKE or ACKNOWLEDGED."),
    INVALID_SEARCH_QUERY(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "Search query must contain between 2 and 100 characters.");

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
