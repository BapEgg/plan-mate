package com.planmate.user.exception;

import com.planmate.common.exception.PlanMateException;

public class InvalidProfileImageException extends PlanMateException {

    public InvalidProfileImageException() {
        super(UserErrorCode.INVALID_PROFILE_IMAGE);
    }

}
