package com.planmate.recommendation.service;

import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Controlled candidate source for reliability experiments.
 *
 * <p>This bean is never selected by default. It removes the external Places API from messaging
 * failure-injection experiments so that ACK/redelivery behavior is the only changing variable.</p>
 */
@Service
@ConditionalOnProperty(name = "app.itinerary.candidates.provider", havingValue = "deterministic")
public class DeterministicCandidateRecommender implements CandidateRecommender {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    private final int candidateCount;
    private final Duration delay;

    public DeterministicCandidateRecommender(
            @Value("${app.itinerary.candidates.target-count:120}") int candidateCount,
            @Value("${app.itinerary.candidates.deterministic-delay:0s}") Duration delay
    ) {
        if (candidateCount <= 0) {
            throw new IllegalArgumentException("candidateCount must be positive");
        }
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("deterministicDelay must not be negative");
        }
        this.candidateCount = candidateCount;
        this.delay = delay;
    }

    @Override
    public List<RecommendedPlaceCandidate> recommend(CandidateRecommendationRequest request) {
        pause();
        CandidateRecommendationRequest.Location center = request.destination().location();
        double latitude = center == null ? SEOUL_LATITUDE : center.latitude();
        double longitude = center == null ? SEOUL_LONGITUDE : center.longitude();
        String destination = request.destination().displayName();

        return IntStream.rangeClosed(1, candidateCount)
                .mapToObj(rank -> candidate(rank, destination, latitude, longitude))
                .toList();
    }

    private void pause() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deterministic reliability provider interrupted", exception);
        }
    }

    private RecommendedPlaceCandidate candidate(
            int rank,
            String destination,
            double latitude,
            double longitude
    ) {
        return new RecommendedPlaceCandidate(
                rank,
                "reliability-place-%03d".formatted(rank),
                "%s 신뢰성 테스트 후보 %03d".formatted(destination, rank),
                "%s 테스트 주소 %03d".formatted(destination, rank),
                new CandidateRecommendationRequest.Location(
                        latitude + rank * 0.00001,
                        longitude + rank * 0.00001
                ),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.0,
                100,
                List.of(),
                List.of("SIGHTSEEING"),
                false,
                rank * 10.0,
                candidateCount - rank + 1.0
        );
    }
}
