package com.planmate.proposal.repository;

import com.planmate.proposal.entity.ItineraryProposalEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryProposalRepository extends JpaRepository<ItineraryProposalEntity, Long> {

    List<ItineraryProposalEntity> findByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    Optional<ItineraryProposalEntity> findByIdAndTripId(Long id, Long tripId);

    Optional<ItineraryProposalEntity> findByTripIdAndCanonicalFingerprintAndStatusIn(
            Long tripId,
            String canonicalFingerprint,
            List<com.planmate.proposal.entity.ItineraryProposalStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT proposal FROM ItineraryProposalEntity proposal WHERE proposal.id = :id AND proposal.tripId = :tripId")
    Optional<ItineraryProposalEntity> findByIdAndTripIdForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);
}
