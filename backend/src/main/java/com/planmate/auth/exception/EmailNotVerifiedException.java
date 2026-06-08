package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class EmailNotVerifiedException extends PlanMateException {

    public EmailNotVerifiedException() {
        super(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }

}
