package com.planmate.regeneration.exception;

import com.planmate.common.exception.PlanMateException;

public class RegenerationException extends PlanMateException {
    public RegenerationException(RegenerationErrorCode errorCode) {
        super(errorCode);
    }
}
