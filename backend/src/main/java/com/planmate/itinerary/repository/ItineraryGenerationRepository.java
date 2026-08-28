package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryGenerationRepository extends JpaRepository<ItineraryGenerationEntity, Long> {

    long countByStatus(ItineraryGenerationStatus status);

    long countByStatusAndUpdatedAtBefore(ItineraryGenerationStatus status, Instant updatedAt);

    Optional<ItineraryGenerationEntity> findFirstByTripIdOrderByCreatedAtDesc(Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ItineraryGenerationEntity> findWithLockById(Long id);

    @Query("""
            select generation
            from ItineraryGenerationEntity generation
            where generation.status = :status
              and (generation.collectionLeaseExpiresAt is null
                   or generation.collectionLeaseExpiresAt <= :now)
            order by generation.collectionLeaseExpiresAt asc nulls first, generation.id asc
            """)
    List<ItineraryGenerationEntity> findStaleCollections(
            @Param("status") ItineraryGenerationStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
