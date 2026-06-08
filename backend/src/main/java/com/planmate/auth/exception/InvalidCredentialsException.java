package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class InvalidCredentialsException extends PlanMateException {

    public InvalidCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS);
    }

}
