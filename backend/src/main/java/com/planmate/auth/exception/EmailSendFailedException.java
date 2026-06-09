package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class EmailSendFailedException extends PlanMateException {

    public EmailSendFailedException(Throwable cause) {
        super(AuthErrorCode.EMAIL_SEND_FAILED, cause);
    }

}
