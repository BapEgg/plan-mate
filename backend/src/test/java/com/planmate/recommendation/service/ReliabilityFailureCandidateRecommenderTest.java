package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.place.api.exception.PlaceProviderRequestRejectedException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReliabilityFailureCandidateRecommenderTest {

    @Test
    void retryableModeAlwaysThrowsProviderUnavailable() {
        ReliabilityFailureCandidateRecommender recommender =
                new ReliabilityFailureCandidateRecommender("retryable", Duration.ZERO);

        assertThatThrownBy(() -> recommender.recommend(null))
                .isInstanceOf(PlaceProviderUnavailableException.class)
                .hasMessage("External place service is unavailable.");
    }

    @Test
    void nonRetryableModeAlwaysThrowsProviderRequestRejected() {
        ReliabilityFailureCandidateRecommender recommender =
                new ReliabilityFailureCandidateRecommender("non-retryable", Duration.ZERO);

        assertThatThrownBy(() -> recommender.recommend(null))
                .isInstanceOf(PlaceProviderRequestRejectedException.class)
                .hasMessage("External place service rejected the application request.");
    }

    @Test
    void rejectsUnknownModeAndNegativeDelay() {
        assertThatThrownBy(() -> new ReliabilityFailureCandidateRecommender("success", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reliabilityFailureMode must be retryable or non-retryable");
        assertThatThrownBy(() -> new ReliabilityFailureCandidateRecommender(
                "retryable",
                Duration.ofMillis(-1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reliabilityFailureDelay must not be negative");
    }
}
