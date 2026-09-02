package com.planmate.vote.exception;

import com.planmate.common.exception.PlanMateException;

public class VoteException extends PlanMateException {
    public VoteException(VoteErrorCode errorCode) {
        super(errorCode);
    }
}
