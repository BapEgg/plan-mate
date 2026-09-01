package com.planmate.membership.repository;

import com.planmate.membership.entity.OwnerTransferRequestEntity;
import com.planmate.membership.entity.OwnerTransferRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerTransferRequestRepository extends JpaRepository<OwnerTransferRequestEntity, Long> {

    Optional<OwnerTransferRequestEntity> findByTripIdAndStatus(Long tripId, OwnerTransferRequestStatus status);

    long countByToUserIdAndStatus(Long toUserId, OwnerTransferRequestStatus status);

    java.util.List<OwnerTransferRequestEntity> findByToUserIdAndStatusOrderByCreatedAtDesc(
            Long toUserId, OwnerTransferRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OwnerTransferRequestEntity r WHERE r.id = :id")
    Optional<OwnerTransferRequestEntity> findByIdForUpdate(@Param("id") Long id);
}
