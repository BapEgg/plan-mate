package com.planmate.proposal.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProposalErrorCode implements ErrorCode {
    PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Itinerary proposal not found."),
    STALE_BASE_VERSION(HttpStatus.CONFLICT, "STALE_BASE_VERSION", "The itinerary changed while this proposal was open."),
    PROPOSAL_NOT_READY(HttpStatus.CONFLICT, "PROPOSAL_NOT_READY", "The proposal cannot be changed in its current state."),
    PROPOSAL_VOTE_BOUND(HttpStatus.CONFLICT, "PROPOSAL_VOTE_BOUND", "A proposal submitted to a vote cannot be directly applied."),
    DUPLICATE_ACTIVE_PROPOSAL(HttpStatus.CONFLICT, "DUPLICATE_ACTIVE_PROPOSAL", "The same itinerary change is already being reviewed."),
    INVALID_PROPOSAL(HttpStatus.BAD_REQUEST, "INVALID_PROPOSAL", "The itinerary change is invalid."),
    PROPOSAL_PLACE_UNRESOLVED(HttpStatus.UNPROCESSABLE_ENTITY, "PROPOSAL_PLACE_UNRESOLVED", "The selected place could not be verified."),
    PROPOSAL_ROUTE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "PROPOSAL_ROUTE_NOT_FOUND", "A required driving route could not be verified."),
    ITINERARY_WINDOW_CLOSED(HttpStatus.CONFLICT, "ITINERARY_WINDOW_CLOSED", "This part of the itinerary can no longer be changed.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProposalErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
