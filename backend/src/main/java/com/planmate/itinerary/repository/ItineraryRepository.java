package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, Long> {

    List<ItineraryEntity> findByTrip_IdOrderByCreatedAtDesc(Long tripId);
}
