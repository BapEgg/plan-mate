package com.planmate.chat.exception;

import com.planmate.common.exception.PlanMateException;

public class ChatException extends PlanMateException {

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode);
    }

    public ChatException(ChatErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
