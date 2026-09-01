package com.planmate.itinerary.route;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteLegSnapshotRepository extends JpaRepository<RouteLegSnapshotEntity, Long> {

    Optional<RouteLegSnapshotEntity> findByTravelModeAndCacheKey(String travelMode, String cacheKey);
}
