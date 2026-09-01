package com.planmate.membership.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MembershipErrorCode implements ErrorCode {

    INVALID_TRIP_TITLE(HttpStatus.BAD_REQUEST, "INVALID_TRIP_TITLE", "Invalid trip title."),
    TARGET_NOT_ACTIVE_MEMBER(HttpStatus.NOT_FOUND, "TARGET_NOT_ACTIVE_MEMBER", "Target is not an active trip member."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE", "Owner must transfer ownership or delete the trip before leaving."),
    OWNER_TRANSFER_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "OWNER_TRANSFER_REQUEST_NOT_FOUND", "Owner transfer request not found."),
    OWNER_TRANSFER_REQUEST_ALREADY_RESOLVED(HttpStatus.CONFLICT, "OWNER_TRANSFER_REQUEST_ALREADY_RESOLVED", "Owner transfer request is already resolved."),
    OWNER_TRANSFER_REQUEST_EXPIRED(HttpStatus.CONFLICT, "OWNER_TRANSFER_REQUEST_EXPIRED", "Owner transfer request has expired."),
    DUPLICATE_OWNER_TRANSFER_REQUEST(HttpStatus.CONFLICT, "DUPLICATE_OWNER_TRANSFER_REQUEST", "An owner transfer request is already open for this trip.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MembershipErrorCode(HttpStatus status, String code, String message) {
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
