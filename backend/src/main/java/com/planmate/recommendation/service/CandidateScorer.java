package com.planmate.recommendation.service;

import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CandidateScorer {

    public double score(CollectedPlaceCandidate candidate, Map<CandidateSearchCategory, Integer> weights) {
        double score = 0;
        for (CandidateSearchCategory category : candidate.sourceCategories()) {
            score += weights.getOrDefault(category, 0) * 10.0;
        }
        if (candidate.sourceCategories().contains(CandidateSearchCategory.CORE_VISIT)
                || candidate.sourceCategories().contains(CandidateSearchCategory.MEAL)) {
            score += 8.0;
        }
        score += Math.max(0, candidate.sourceCategories().size() - 1) * 6.0;
        if (candidate.rating() != null) {
            score += candidate.rating() * 4.0;
        }
        if (candidate.userRatingCount() != null && candidate.userRatingCount() > 0) {
            score += Math.log10(candidate.userRatingCount() + 1) * 3.0;
        }
        if (candidate.distanceMeters() != Double.MAX_VALUE) {
            score += Math.max(0, 8.0 - candidate.distanceMeters() / 5_000);
        }
        return score;
    }
}
