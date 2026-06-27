package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.trip.domain.TripInterest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateCategoryWeightCalculatorTest {

    private final CandidateCategoryWeightCalculator calculator = new CandidateCategoryWeightCalculator();

    @Test
    void foodIncreasesMealWeightWithoutFoodBucket() {
        Map<CandidateSearchCategory, Integer> weights = calculator.calculate(List.of(
                TripInterest.FOOD,
                TripInterest.CAFE,
                TripInterest.CULTURE,
                TripInterest.NATURE,
                TripInterest.SHOPPING
        ));

        assertThat(weights).containsEntry(CandidateSearchCategory.CORE_VISIT, 2);
        assertThat(weights).containsEntry(CandidateSearchCategory.MEAL, 3);
        assertThat(weights).containsEntry(CandidateSearchCategory.CAFE, 1);
        assertThat(weights).containsEntry(CandidateSearchCategory.CULTURE, 1);
        assertThat(weights).containsEntry(CandidateSearchCategory.NATURE, 1);
        assertThat(weights).containsEntry(CandidateSearchCategory.SHOPPING, 1);
        assertThat(weights).doesNotContainKey(null);
    }

    @Test
    void sightseeingIncreasesCoreVisitWeightWithoutDuplicateBucket() {
        Map<CandidateSearchCategory, Integer> weights = calculator.calculate(List.of(TripInterest.SIGHTSEEING));

        assertThat(weights).containsEntry(CandidateSearchCategory.CORE_VISIT, 3);
        assertThat(weights).containsEntry(CandidateSearchCategory.MEAL, 2);
        assertThat(weights).hasSize(2);
    }
}
