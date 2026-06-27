package com.planmate.recommendation.service;

import com.planmate.recommendation.domain.CandidateSearchCategory;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CandidateQuotaCalculator {

    public Map<CandidateSearchCategory, Integer> calculate(Map<CandidateSearchCategory, Integer> weights, int targetCount) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        EnumMap<CandidateSearchCategory, Integer> quotas = new EnumMap<>(CandidateSearchCategory.class);
        if (totalWeight <= 0 || targetCount <= 0) {
            return quotas;
        }

        int assigned = 0;
        for (Map.Entry<CandidateSearchCategory, Integer> entry : weights.entrySet()) {
            int quota = Math.max(1, targetCount * entry.getValue() / totalWeight);
            quotas.put(entry.getKey(), quota);
            assigned += quota;
        }

        while (assigned < targetCount) {
            CandidateSearchCategory category = weights.entrySet()
                    .stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            quotas.merge(category, 1, Integer::sum);
            assigned++;
        }

        while (assigned > targetCount) {
            CandidateSearchCategory category = quotas.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() > 1)
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (category == null) {
                break;
            }
            quotas.merge(category, -1, Integer::sum);
            assigned--;
        }
        return Map.copyOf(quotas);
    }
}
