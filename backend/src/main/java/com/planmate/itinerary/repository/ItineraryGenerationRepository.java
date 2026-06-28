package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ItineraryGenerationRepository extends JpaRepository<ItineraryGenerationEntity, Long> {

    @EntityGraph(attributePaths = {"trip"})
    Optional<ItineraryGenerationEntity> findWithTripById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ItineraryGenerationEntity> findWithLockById(Long id);
}
