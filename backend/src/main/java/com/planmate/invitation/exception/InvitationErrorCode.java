package com.planmate.invitation.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InvitationErrorCode implements ErrorCode {

    INVITEE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITEE_NOT_FOUND", "No account found for that email."),
    INVITEE_ALREADY_ACTIVE_MEMBER(HttpStatus.CONFLICT, "INVITEE_ALREADY_ACTIVE_MEMBER", "This user is already a trip member."),
    DUPLICATE_PENDING_INVITATION(HttpStatus.CONFLICT, "DUPLICATE_PENDING_INVITATION", "This user already has a pending invitation to this trip."),
    TRIP_MEMBER_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "TRIP_MEMBER_CAPACITY_EXCEEDED", "This trip has reached its 20-member limit."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitation not found."),
    INVITATION_ALREADY_RESOLVED(HttpStatus.CONFLICT, "INVITATION_ALREADY_RESOLVED", "This invitation is already resolved."),
    INVITATION_EXPIRED(HttpStatus.CONFLICT, "INVITATION_EXPIRED", "This invitation has expired.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InvitationErrorCode(HttpStatus status, String code, String message) {
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
