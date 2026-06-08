package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class ExpiredAuthTokenException extends PlanMateException {

    public ExpiredAuthTokenException() {
        super(AuthErrorCode.EXPIRED_TOKEN);
    }

}
