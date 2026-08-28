package com.planmate.itinerary.service;

import com.planmate.common.exception.PlanMateException;
import com.planmate.place.api.exception.PlaceApiException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class WorkerFailureClassifier {

    private static final String TRANSIENT_DATA_ACCESS_FAILURE = "TRANSIENT_DATA_ACCESS_FAILURE";
    private static final String INVALID_WORKER_INPUT = "INVALID_WORKER_INPUT";
    private static final String WORKER_PROCESSING_FAILED = "WORKER_PROCESSING_FAILED";

    public WorkerFailure classify(RuntimeException exception) {
        if (exception instanceof PlaceProviderUnavailableException placeFailure) {
            return WorkerFailure.retryable(placeFailure.code());
        }
        if (exception instanceof TransientDataAccessException) {
            return WorkerFailure.retryable(TRANSIENT_DATA_ACCESS_FAILURE);
        }
        if (exception instanceof PlanMateException planMateException) {
            return WorkerFailure.nonRetryable(planMateException.code());
        }
        if (exception instanceof PlaceApiException placeApiException) {
            return WorkerFailure.nonRetryable(placeApiException.code());
        }
        if (exception instanceof IllegalArgumentException) {
            return WorkerFailure.nonRetryable(INVALID_WORKER_INPUT);
        }
        return WorkerFailure.nonRetryable(WORKER_PROCESSING_FAILED);
    }

    public record WorkerFailure(boolean retryable, String reason) {

        static WorkerFailure retryable(String reason) {
            return new WorkerFailure(true, reason);
        }

        static WorkerFailure nonRetryable(String reason) {
            return new WorkerFailure(false, reason);
        }
    }
}
