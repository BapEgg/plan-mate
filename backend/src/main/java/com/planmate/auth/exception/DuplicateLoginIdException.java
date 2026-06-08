package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class DuplicateLoginIdException extends PlanMateException {

    public DuplicateLoginIdException() {
        super(AuthErrorCode.DUPLICATE_LOGIN_ID);
    }

}
