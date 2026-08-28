package com.planmate.recommendation.api;

import java.util.List;

public interface CandidateRecommender {

    List<RecommendedPlaceCandidate> recommend(CandidateRecommendationRequest request);
}
