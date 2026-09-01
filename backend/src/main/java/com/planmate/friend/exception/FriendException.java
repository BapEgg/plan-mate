package com.planmate.friend.exception;

import com.planmate.common.exception.PlanMateException;

public class FriendException extends PlanMateException {

    public FriendException(FriendErrorCode errorCode) {
        super(errorCode);
    }

    public FriendException(FriendErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
