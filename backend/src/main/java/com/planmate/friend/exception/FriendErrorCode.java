package com.planmate.friend.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FriendErrorCode implements ErrorCode {

    ADDRESSEE_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESSEE_NOT_FOUND", "No account found for that email."),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "ALREADY_FRIENDS", "You are already friends."),
    DUPLICATE_PENDING_FRIEND_REQUEST(HttpStatus.CONFLICT, "DUPLICATE_PENDING_FRIEND_REQUEST", "A friend request is already pending between these accounts."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_REQUEST_NOT_FOUND", "Friend request not found."),
    FRIEND_REQUEST_ALREADY_RESOLVED(HttpStatus.CONFLICT, "FRIEND_REQUEST_ALREADY_RESOLVED", "This friend request is already resolved.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    FriendErrorCode(HttpStatus status, String code, String message) {
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
