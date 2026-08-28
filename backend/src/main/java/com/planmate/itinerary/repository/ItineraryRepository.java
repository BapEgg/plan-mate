package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, Long> {

    List<ItineraryEntity> findByTripIdOrderByCreatedAtDesc(Long tripId);

    Optional<ItineraryEntity> findFirstByTripIdOrderByCreatedAtDesc(Long tripId);

    Optional<ItineraryEntity> findByGeneration_Id(Long generationId);
}
