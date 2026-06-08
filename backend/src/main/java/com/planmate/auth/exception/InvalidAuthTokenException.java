package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class InvalidAuthTokenException extends PlanMateException {

    public InvalidAuthTokenException() {
        super(AuthErrorCode.INVALID_TOKEN);
    }

}
