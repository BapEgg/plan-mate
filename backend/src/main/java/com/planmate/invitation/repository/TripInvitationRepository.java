package com.planmate.invitation.repository;

import com.planmate.invitation.entity.InvitationStatus;
import com.planmate.invitation.entity.TripInvitationEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripInvitationRepository extends JpaRepository<TripInvitationEntity, Long> {

    Optional<TripInvitationEntity> findByTripIdAndInviteeUserIdAndStatus(
            Long tripId, Long inviteeUserId, InvitationStatus status
    );

    List<TripInvitationEntity> findByInviteeUserIdAndStatusOrderByCreatedAtDesc(Long inviteeUserId, InvitationStatus status);

    long countByInviteeUserIdAndStatus(Long inviteeUserId, InvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TripInvitationEntity i WHERE i.id = :id")
    Optional<TripInvitationEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(i) FROM TripInvitationEntity i
            WHERE i.tripId = :tripId AND i.status = com.planmate.invitation.entity.InvitationStatus.PENDING
              AND i.expiresAt > :now
            """)
    long countActivePendingByTripId(@Param("tripId") Long tripId, @Param("now") Instant now);
}
