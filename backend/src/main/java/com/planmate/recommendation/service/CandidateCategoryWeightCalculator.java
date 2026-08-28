package com.planmate.recommendation.service;

import com.planmate.recommendation.api.Interest;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CandidateCategoryWeightCalculator {

    public Map<CandidateSearchCategory, Integer> calculate(List<Interest> interests) {
        EnumMap<CandidateSearchCategory, Integer> weights = new EnumMap<>(CandidateSearchCategory.class);
        weights.put(CandidateSearchCategory.CORE_VISIT, 2);
        weights.put(CandidateSearchCategory.MEAL, 2);

        for (Interest interest : interests) {
            switch (interest) {
                case FOOD -> weights.merge(CandidateSearchCategory.MEAL, 1, Integer::sum);
                case SIGHTSEEING -> weights.merge(CandidateSearchCategory.CORE_VISIT, 1, Integer::sum);
                case CAFE -> weights.putIfAbsent(CandidateSearchCategory.CAFE, 1);
                case CULTURE -> weights.putIfAbsent(CandidateSearchCategory.CULTURE, 1);
                case NATURE -> weights.putIfAbsent(CandidateSearchCategory.NATURE, 1);
                case SHOPPING -> weights.putIfAbsent(CandidateSearchCategory.SHOPPING, 1);
                case PHOTO -> weights.putIfAbsent(CandidateSearchCategory.PHOTO, 1);
                case NIGHT_VIEW -> weights.putIfAbsent(CandidateSearchCategory.NIGHT_VIEW, 1);
                case ACTIVITY -> weights.putIfAbsent(CandidateSearchCategory.ACTIVITY, 1);
                case REST -> weights.putIfAbsent(CandidateSearchCategory.REST, 1);
                case ART -> weights.putIfAbsent(CandidateSearchCategory.ART, 1);
                case THEME_PARK -> weights.putIfAbsent(CandidateSearchCategory.THEME_PARK, 1);
                case LOCAL -> weights.putIfAbsent(CandidateSearchCategory.LOCAL, 1);
            }
        }
        return Map.copyOf(weights);
    }
}
