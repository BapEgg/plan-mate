package com.planmate.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceSearchCandidate;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.recommendation.service.PlaceCandidateAccumulator.CategorizedPlaceSearchCandidate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateSelectionTest {

    @Test
    void accumulatorMergesSourceCategoriesByPlaceId() {
        PlaceCandidateAccumulator accumulator = new PlaceCandidateAccumulator();
        PlaceSearchCandidate samePlace = candidate("place-1", "Temple");

        List<CollectedPlaceCandidate> result = accumulator.accumulate(List.of(
                new CategorizedPlaceSearchCandidate(samePlace, CandidateSearchCategory.CORE_VISIT, 100),
                new CategorizedPlaceSearchCandidate(samePlace, CandidateSearchCategory.CULTURE, 80)
        ));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourceCategories())
                .containsExactlyInAnyOrder(CandidateSearchCategory.CORE_VISIT, CandidateSearchCategory.CULTURE);
        assertThat(result.getFirst().distanceMeters()).isEqualTo(80);
    }

    @Test
    void haversineCalculatesApproximateDistance() {
        HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

        double distance = calculator.distanceMeters(new GeoPoint(37.5665, 126.9780), new GeoPoint(35.1796, 129.0756));

        assertThat(distance).isBetween(320_000.0, 340_000.0);
    }

    @Test
    void selectorLimitsCandidatesToTargetCount() {
        CandidateSelector selector = new CandidateSelector(new CandidateQuotaCalculator());
        Map<CandidateSearchCategory, Integer> weights = new EnumMap<>(CandidateSearchCategory.class);
        weights.put(CandidateSearchCategory.CORE_VISIT, 2);
        weights.put(CandidateSearchCategory.MEAL, 2);

        List<CollectedPlaceCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 140; index++) {
            candidates.add(collected("place-" + index, index));
        }

        List<CollectedPlaceCandidate> selected = selector.select(candidates, weights, 120);

        assertThat(selected).hasSize(120);
        assertThat(selected).extracting(CollectedPlaceCandidate::placeId).doesNotHaveDuplicates();
    }

    @Test
    void selectorCanCarryMustVisitCategory() {
        CollectedPlaceCandidate candidate = new CollectedPlaceCandidate(
                "must-place",
                "Must Place",
                "address",
                new GeoPoint(35.0, 135.0),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                null,
                null,
                List.of(),
                List.of(CandidateSearchCategory.MUST_VISIT),
                0,
                Double.MAX_VALUE
        );

        assertThat(candidate.sourceCategories()).containsExactly(CandidateSearchCategory.MUST_VISIT);
    }

    @Test
    void quotaCalculatorAllocatesByWeight() {
        CandidateQuotaCalculator calculator = new CandidateQuotaCalculator();
        Map<CandidateSearchCategory, Integer> weights = new EnumMap<>(CandidateSearchCategory.class);
        weights.put(CandidateSearchCategory.CORE_VISIT, 2);
        weights.put(CandidateSearchCategory.MEAL, 3);
        weights.put(CandidateSearchCategory.CAFE, 1);

        Map<CandidateSearchCategory, Integer> quotas = calculator.calculate(weights, 12);

        assertThat(quotas.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(12);
        assertThat(quotas.get(CandidateSearchCategory.MEAL)).isGreaterThan(quotas.get(CandidateSearchCategory.CAFE));
    }

    private PlaceSearchCandidate candidate(String placeId, String name) {
        return new PlaceSearchCandidate(
                placeId,
                name,
                "address",
                new GeoPoint(35.0, 135.0),
                List.of("tourist_attraction"),
                "tourist_attraction",
                "OPERATIONAL",
                4.5,
                100,
                List.of("09:00-18:00")
        );
    }

    private CollectedPlaceCandidate collected(String placeId, int index) {
        CandidateSearchCategory category = index % 2 == 0 ? CandidateSearchCategory.CORE_VISIT : CandidateSearchCategory.MEAL;
        return new CollectedPlaceCandidate(
                placeId,
                "Place " + index,
                "address",
                new GeoPoint(35.0, 135.0),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.0,
                100 + index,
                List.of(),
                List.of(category),
                index,
                200 - index
        );
    }
}
