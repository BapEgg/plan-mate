package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class TokenAlreadyUsedException extends PlanMateException {

    public TokenAlreadyUsedException() {
        super(AuthErrorCode.TOKEN_ALREADY_USED);
    }

}
