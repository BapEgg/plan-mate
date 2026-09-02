package com.planmate.regeneration.repository;

import com.planmate.regeneration.entity.ItineraryRegenerationEntity;
import com.planmate.regeneration.entity.ItineraryRegenerationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryRegenerationRepository extends JpaRepository<ItineraryRegenerationEntity, Long> {

    Optional<ItineraryRegenerationEntity> findByGenerationId(Long generationId);

    Optional<ItineraryRegenerationEntity> findFirstByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    List<ItineraryRegenerationEntity> findByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    boolean existsByTripIdAndStatusIn(Long tripId, Collection<ItineraryRegenerationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT regeneration FROM ItineraryRegenerationEntity regeneration WHERE regeneration.id = :id AND regeneration.tripId = :tripId")
    Optional<ItineraryRegenerationEntity> findByIdAndTripIdForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT regeneration FROM ItineraryRegenerationEntity regeneration WHERE regeneration.generationId = :generationId")
    Optional<ItineraryRegenerationEntity> findByGenerationIdForUpdate(@Param("generationId") Long generationId);
}
