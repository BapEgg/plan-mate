package com.planmate.place.exception;

import com.planmate.common.exception.PlanMateException;

public class InvalidPlaceIdException extends PlanMateException {

    public InvalidPlaceIdException() {
        super(PlaceErrorCode.INVALID_PLACE_ID);
    }

}
