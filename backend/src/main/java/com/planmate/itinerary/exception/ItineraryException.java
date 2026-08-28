package com.planmate.itinerary.exception;

import com.planmate.common.exception.PlanMateException;

public class ItineraryException extends PlanMateException {

    public ItineraryException(ItineraryErrorCode errorCode) {
        super(errorCode);
    }

    public ItineraryException(ItineraryErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ItineraryException(ItineraryErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
