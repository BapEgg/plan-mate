package com.planmate.user.exception;

import com.planmate.common.exception.PlanMateException;

public class UserNotFoundException extends PlanMateException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }

}
