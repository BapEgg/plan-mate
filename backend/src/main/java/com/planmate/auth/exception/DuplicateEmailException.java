package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class DuplicateEmailException extends PlanMateException {

    public DuplicateEmailException() {
        super(AuthErrorCode.DUPLICATE_EMAIL);
    }

}
