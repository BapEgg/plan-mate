package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.place.api.exception.PlaceProviderConfigurationException;
import com.planmate.place.api.exception.PlaceProviderRequestRejectedException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class WorkerFailureClassifierTest {

    private final WorkerFailureClassifier classifier = new WorkerFailureClassifier();

    @Test
    void classifiesTemporaryProviderAndDataAccessFailuresAsRetryable() {
        assertThat(classifier.classify(new PlaceProviderUnavailableException()))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(true, "PLACE_PROVIDER_UNAVAILABLE"));
        assertThat(classifier.classify(new QueryTimeoutException("database timeout")))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(true, "TRANSIENT_DATA_ACCESS_FAILURE"));
    }

    @Test
    void classifiesInvariantProviderRequestAndConfigurationFailuresAsNonRetryable() {
        assertThat(classifier.classify(new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY)).retryable())
                .isFalse();
        assertThat(classifier.classify(new PlaceProviderConfigurationException()))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(false, "PLACE_PROVIDER_CONFIGURATION_ERROR"));
        assertThat(classifier.classify(new PlaceProviderRequestRejectedException(new IllegalStateException())))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(false, "PLACE_PROVIDER_REQUEST_REJECTED"));
    }

    @Test
    void usesStableFallbackCodesInsteadOfRuntimeClassNames() {
        assertThat(classifier.classify(new IllegalArgumentException("bad message")))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(false, "INVALID_WORKER_INPUT"));
        assertThat(classifier.classify(new IllegalStateException("bug")))
                .isEqualTo(new WorkerFailureClassifier.WorkerFailure(false, "WORKER_PROCESSING_FAILED"));
    }
}
