package com.planmate.recommendation.service;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.PlaceSearchCandidate;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaceCandidateAccumulator {

    public List<CollectedPlaceCandidate> accumulate(List<CategorizedPlaceSearchCandidate> candidates) {
        Map<String, MutableCandidate> merged = new LinkedHashMap<>();
        for (CategorizedPlaceSearchCandidate item : candidates) {
            PlaceSearchCandidate candidate = item.candidate();
            if (!StringUtils.hasText(candidate.placeId())) {
                continue;
            }
            merged.compute(candidate.placeId(), (placeId, existing) -> {
                if (existing == null) {
                    return new MutableCandidate(candidate, item.category(), item.distanceMeters());
                }
                existing.addCategory(item.category());
                existing.keepShorterDistance(item.distanceMeters());
                return existing;
            });
        }
        return merged.values().stream().map(MutableCandidate::toCandidate).toList();
    }

    public record CategorizedPlaceSearchCandidate(
            PlaceSearchCandidate candidate,
            CandidateSearchCategory category,
            double distanceMeters
    ) {
    }

    private static final class MutableCandidate {
        private final PlaceSearchCandidate candidate;
        private final EnumSet<CandidateSearchCategory> categories;
        private double distanceMeters;

        private MutableCandidate(PlaceSearchCandidate candidate, CandidateSearchCategory category, double distanceMeters) {
            this.candidate = candidate;
            this.categories = EnumSet.of(category);
            this.distanceMeters = distanceMeters;
        }

        private void addCategory(CandidateSearchCategory category) {
            categories.add(category);
        }

        private void keepShorterDistance(double nextDistanceMeters) {
            if (nextDistanceMeters < distanceMeters) {
                distanceMeters = nextDistanceMeters;
            }
        }

        private CollectedPlaceCandidate toCandidate() {
            GeoPoint location = candidate.location();
            return new CollectedPlaceCandidate(
                    candidate.placeId(),
                    candidate.displayName(),
                    candidate.formattedAddress(),
                    location,
                    candidate.primaryType(),
                    candidate.types(),
                    candidate.businessStatus(),
                    candidate.rating(),
                    candidate.userRatingCount(),
                    candidate.openingPeriods(),
                    new ArrayList<>(categories),
                    distanceMeters,
                    0
            );
        }
    }
}
