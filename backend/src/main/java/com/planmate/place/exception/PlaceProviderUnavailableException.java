package com.planmate.place.exception;

import com.planmate.common.exception.PlanMateException;

public class PlaceProviderUnavailableException extends PlanMateException {

    public PlaceProviderUnavailableException() {
        super(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }

    public PlaceProviderUnavailableException(Throwable cause) {
        super(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE, cause);
    }

}
