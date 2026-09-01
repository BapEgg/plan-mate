package com.planmate.membership.exception;

import com.planmate.common.exception.PlanMateException;

public class MembershipException extends PlanMateException {

    public MembershipException(MembershipErrorCode errorCode) {
        super(errorCode);
    }

    public MembershipException(MembershipErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
