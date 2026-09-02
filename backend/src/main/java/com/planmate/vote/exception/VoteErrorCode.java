package com.planmate.vote.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum VoteErrorCode implements ErrorCode {
    VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "VOTE_NOT_FOUND", "Vote not found."),
    VOTE_ALREADY_CLOSED(HttpStatus.CONFLICT, "VOTE_ALREADY_CLOSED", "This vote is already closed."),
    NOT_ELIGIBLE_VOTER(HttpStatus.FORBIDDEN, "NOT_ELIGIBLE_VOTER", "You are not eligible to vote on this proposal."),
    VOTE_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "VOTE_CANCEL_FORBIDDEN", "Only the proposer or current owner can cancel this vote.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    VoteErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
