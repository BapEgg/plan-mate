package com.planmate.recommendation.service;

import com.planmate.recommendation.domain.PlaceTypePolicyRule;
import com.planmate.recommendation.entity.PlaceTypePolicyEntity;
import com.planmate.recommendation.repository.PlaceTypePolicyRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceTypePolicyService {

    private final PlaceTypePolicyRepository placeTypePolicyRepository;

    public PlaceTypePolicyService(PlaceTypePolicyRepository placeTypePolicyRepository) {
        this.placeTypePolicyRepository = placeTypePolicyRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, PlaceTypePolicyRule> loadEnabledPoliciesByTypeName() {
        Map<String, PlaceTypePolicyRule> policies = placeTypePolicyRepository.findByEnabledTrue()
                .stream()
                .collect(Collectors.toMap(
                        PlaceTypePolicyEntity::getTypeName,
                        this::toRule,
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));
        return Map.copyOf(policies);
    }

    private PlaceTypePolicyRule toRule(PlaceTypePolicyEntity policy) {
        return new PlaceTypePolicyRule(
                policy.getTypeName(),
                policy.getPolicy(),
                policy.getScoreAdjustment(),
                policy.getReason()
        );
    }
}
