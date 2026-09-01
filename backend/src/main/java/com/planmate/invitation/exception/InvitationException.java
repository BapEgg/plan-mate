package com.planmate.invitation.exception;

import com.planmate.common.exception.PlanMateException;

public class InvitationException extends PlanMateException {

    public InvitationException(InvitationErrorCode errorCode) {
        super(errorCode);
    }

    public InvitationException(InvitationErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
