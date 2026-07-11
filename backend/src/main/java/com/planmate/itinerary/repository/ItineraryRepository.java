package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, Long> {

    List<ItineraryEntity> findByTrip_IdOrderByCreatedAtDesc(Long tripId);

    Optional<ItineraryEntity> findFirstByTrip_IdOrderByCreatedAtDesc(Long tripId);
}
