package com.planmate.recommendation.service;

import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CandidateSelector {

    private final CandidateQuotaCalculator quotaCalculator;

    public CandidateSelector(CandidateQuotaCalculator quotaCalculator) {
        this.quotaCalculator = quotaCalculator;
    }

    public List<CollectedPlaceCandidate> select(
            List<CollectedPlaceCandidate> candidates,
            Map<CandidateSearchCategory, Integer> weights,
            int targetCount
    ) {
        List<CollectedPlaceCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(CollectedPlaceCandidate::score).reversed())
                .toList();
        Map<CandidateSearchCategory, Integer> quotas = quotaCalculator.calculate(weights, targetCount);
        LinkedHashMap<String, CollectedPlaceCandidate> selected = new LinkedHashMap<>();

        for (Map.Entry<CandidateSearchCategory, Integer> quota : quotas.entrySet()) {
            int added = 0;
            for (CollectedPlaceCandidate candidate : sorted) {
                if (selected.containsKey(candidate.placeId()) || !candidate.sourceCategories().contains(quota.getKey())) {
                    continue;
                }
                selected.put(candidate.placeId(), candidate);
                added++;
                if (added >= quota.getValue() || selected.size() >= targetCount) {
                    break;
                }
            }
            if (selected.size() >= targetCount) {
                break;
            }
        }

        for (CollectedPlaceCandidate candidate : sorted) {
            if (selected.size() >= targetCount) {
                break;
            }
            selected.putIfAbsent(candidate.placeId(), candidate);
        }

        return new ArrayList<>(selected.values());
    }
}
