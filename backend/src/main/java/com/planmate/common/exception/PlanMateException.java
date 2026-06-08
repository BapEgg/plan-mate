package com.planmate.common.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

public abstract class PlanMateException extends RuntimeException {

    private final ErrorCode errorCode;

    protected PlanMateException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    protected PlanMateException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    protected PlanMateException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    protected PlanMateException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public HttpStatus status() {
        return errorCode.status();
    }

    public String code() {
        return errorCode.code();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

}
