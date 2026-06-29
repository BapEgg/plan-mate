package com.planmate.recommendation.domain;

public record PlaceTypePolicyRule(
        String typeName,
        PlaceTypePolicy policy,
        double scoreAdjustment,
        String reason
) {

    public boolean isBlock() {
        return policy == PlaceTypePolicy.BLOCK;
    }
}
