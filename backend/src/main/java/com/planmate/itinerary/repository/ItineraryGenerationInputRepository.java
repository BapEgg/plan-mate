package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryGenerationInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryGenerationInputRepository extends JpaRepository<ItineraryGenerationInputEntity, Long> {
}
