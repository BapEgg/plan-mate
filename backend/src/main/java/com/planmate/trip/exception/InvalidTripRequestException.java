package com.planmate.trip.exception;

import com.planmate.common.exception.PlanMateException;

public class InvalidTripRequestException extends PlanMateException {

    public InvalidTripRequestException(String message) {
        super(TripErrorCode.INVALID_TRIP_REQUEST, message);
    }

}
