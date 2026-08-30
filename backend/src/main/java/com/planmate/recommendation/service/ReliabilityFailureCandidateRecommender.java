package com.planmate.recommendation.service;

import com.planmate.place.api.exception.PlaceProviderRequestRejectedException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic failure source used only by reliability experiments.
 *
 * <p>The production default remains the Google provider. This bean is selected only when the
 * candidate provider is explicitly set to {@code reliability-failure}.</p>
 */
@Service
@ConditionalOnProperty(name = "app.itinerary.candidates.provider", havingValue = "reliability-failure")
public class ReliabilityFailureCandidateRecommender implements CandidateRecommender {

    static final String RETRYABLE = "retryable";
    static final String NON_RETRYABLE = "non-retryable";

    private final String mode;
    private final Duration delay;

    public ReliabilityFailureCandidateRecommender(
            @Value("${app.itinerary.candidates.reliability-failure-mode}") String mode,
            @Value("${app.itinerary.candidates.reliability-failure-delay:0s}") Duration delay
    ) {
        this.mode = normalize(mode);
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("reliabilityFailureDelay must not be negative");
        }
        this.delay = delay;
    }

    @Override
    public List<RecommendedPlaceCandidate> recommend(CandidateRecommendationRequest request) {
        pause();
        if (RETRYABLE.equals(mode)) {
            throw new PlaceProviderUnavailableException();
        }
        throw new PlaceProviderRequestRejectedException(
                new IllegalStateException("Controlled non-retryable reliability experiment failure")
        );
    }

    private void pause() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!RETRYABLE.equals(normalized) && !NON_RETRYABLE.equals(normalized)) {
            throw new IllegalArgumentException(
                    "reliabilityFailureMode must be retryable or non-retryable"
            );
        }
        return normalized;
    }
}
