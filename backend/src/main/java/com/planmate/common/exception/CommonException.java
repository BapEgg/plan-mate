package com.planmate.common.exception;

public class CommonException extends PlanMateException {

    public CommonException(CommonErrorCode errorCode) {
        super(errorCode);
    }

    public CommonException(CommonErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
