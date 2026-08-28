package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import org.springframework.stereotype.Component;

@Component
public class GenerationCandidateSnapshotMapper {

    public GenerationCandidateSnapshot map(RecommendedPlaceCandidate candidate) {
        return new GenerationCandidateSnapshot(
                candidate.rank(),
                candidate.placeId(),
                candidate.displayName(),
                candidate.formattedAddress(),
                toLocation(candidate.location()),
                candidate.primaryType(),
                candidate.types(),
                candidate.businessStatus(),
                candidate.rating(),
                candidate.userRatingCount(),
                candidate.openingPeriods(),
                candidate.sourceCategories(),
                candidate.forcedMustVisit(),
                candidate.distanceMeters(),
                candidate.score()
        );
    }

    public GenerationCandidateSnapshot map(GenerationCandidateSnapshotEntity entity) {
        return new GenerationCandidateSnapshot(
                entity.getRank(),
                entity.getPlaceId(),
                entity.getName(),
                entity.getAddress(),
                new GenerationCandidateSnapshot.Location(entity.getLatitude(), entity.getLongitude()),
                entity.getPrimaryType(),
                entity.getTypes(),
                entity.getBusinessStatus(),
                entity.getRating(),
                entity.getUserRatingCount(),
                entity.getOpeningPeriods(),
                entity.getSourceCategories(),
                entity.isForcedMustVisit(),
                entity.getDistanceMeters(),
                entity.getScore()
        );
    }

    private GenerationCandidateSnapshot.Location toLocation(CandidateRecommendationRequest.Location location) {
        return location == null
                ? null
                : new GenerationCandidateSnapshot.Location(location.latitude(), location.longitude());
    }
}
