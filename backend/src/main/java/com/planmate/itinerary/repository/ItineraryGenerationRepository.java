package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryGenerationRepository extends JpaRepository<ItineraryGenerationEntity, Long> {

    @EntityGraph(attributePaths = {"trip"})
    Optional<ItineraryGenerationEntity> findWithTripById(Long id);
}
