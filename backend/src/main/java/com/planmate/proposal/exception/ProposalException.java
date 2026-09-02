package com.planmate.proposal.exception;

import com.planmate.common.exception.PlanMateException;

public class ProposalException extends PlanMateException {
    public ProposalException(ProposalErrorCode errorCode) {
        super(errorCode);
    }
}
