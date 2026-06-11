package com.planmate.trip.exception;

import com.planmate.common.exception.PlanMateException;

public class TripNotFoundException extends PlanMateException {

    public TripNotFoundException() {
        super(TripErrorCode.TRIP_NOT_FOUND);
    }

}
