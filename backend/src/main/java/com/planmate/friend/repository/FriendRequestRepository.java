package com.planmate.friend.repository;

import com.planmate.friend.entity.FriendRequestEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, Long> {

    List<FriendRequestEntity> findByAddresseeUserIdAndStatusOrderByCreatedAtDesc(
            Long addresseeUserId, com.planmate.friend.entity.FriendRequestStatus status
    );

    List<FriendRequestEntity> findByRequesterUserIdAndStatusOrderByCreatedAtDesc(
            Long requesterUserId, com.planmate.friend.entity.FriendRequestStatus status
    );

    long countByAddresseeUserIdAndStatus(Long addresseeUserId, com.planmate.friend.entity.FriendRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FriendRequestEntity r WHERE r.id = :id")
    Optional<FriendRequestEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT r FROM FriendRequestEntity r
            WHERE r.status = com.planmate.friend.entity.FriendRequestStatus.PENDING
              AND ((r.requesterUserId = :userA AND r.addresseeUserId = :userB)
                OR (r.requesterUserId = :userB AND r.addresseeUserId = :userA))
            """)
    Optional<FriendRequestEntity> findPendingBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
            SELECT COUNT(r) > 0 FROM FriendRequestEntity r
            WHERE r.status = com.planmate.friend.entity.FriendRequestStatus.ACCEPTED
              AND ((r.requesterUserId = :userA AND r.addresseeUserId = :userB)
                OR (r.requesterUserId = :userB AND r.addresseeUserId = :userA))
            """)
    boolean areFriends(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
            SELECT r FROM FriendRequestEntity r
            WHERE r.status = com.planmate.friend.entity.FriendRequestStatus.ACCEPTED
              AND (r.requesterUserId = :userId OR r.addresseeUserId = :userId)
            ORDER BY r.respondedAt DESC
            """)
    List<FriendRequestEntity> findAcceptedInvolving(@Param("userId") Long userId);
}
