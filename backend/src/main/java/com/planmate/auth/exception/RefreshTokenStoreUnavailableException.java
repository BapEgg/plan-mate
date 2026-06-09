package com.planmate.auth.exception;

import com.planmate.common.exception.PlanMateException;

public class RefreshTokenStoreUnavailableException extends PlanMateException {

    public RefreshTokenStoreUnavailableException(Throwable cause) {
        super(AuthErrorCode.REFRESH_TOKEN_STORE_UNAVAILABLE, cause);
    }

}
