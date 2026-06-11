package com.planmate.user.exception;

import com.planmate.common.exception.PlanMateException;

public class ProfileImageSaveFailedException extends PlanMateException {

    public ProfileImageSaveFailedException(Throwable cause) {
        super(UserErrorCode.PROFILE_IMAGE_SAVE_FAILED, cause);
    }

}
