package com.planmate.recommendation.domain;

public record CandidateSearchQuery(
        CandidateSearchCategory category,
        String textQuery
) {
}
