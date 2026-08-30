package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicCandidateRecommenderTest {

    @Test
    void returnsRequestedNumberOfStableUniqueCandidates() {
        DeterministicCandidateRecommender recommender = new DeterministicCandidateRecommender(120, Duration.ZERO);
        CandidateRecommendationRequest request = new CandidateRecommendationRequest(
                new CandidateRecommendationRequest.Destination(
                        "서울특별시",
                        new CandidateRecommendationRequest.Location(37.5665, 126.9780),
                        null
                ),
                List.of(),
                null,
                List.of()
        );

        List<RecommendedPlaceCandidate> candidates = recommender.recommend(request);

        assertThat(candidates).hasSize(120);
        assertThat(candidates).extracting(RecommendedPlaceCandidate::rank)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 120).boxed().toList());
        assertThat(candidates).extracting(RecommendedPlaceCandidate::placeId)
                .doesNotHaveDuplicates();
        assertThat(candidates.getFirst().placeId()).isEqualTo("reliability-place-001");
        assertThat(candidates.getLast().placeId()).isEqualTo("reliability-place-120");
    }
}
