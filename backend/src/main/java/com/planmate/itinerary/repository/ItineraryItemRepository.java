package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItemEntity, Long> {
}
