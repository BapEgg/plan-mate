package com.planmate.trip.repository;

import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMemberEntity, Long> {

    @EntityGraph(attributePaths = {"trip"})
    List<TripMemberEntity> findByUser_IdAndStatusOrderByTrip_CreatedAtDesc(Long userId, MembershipStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<TripMemberEntity> findByTrip_IdAndStatusOrderByCreatedAtAsc(Long tripId, MembershipStatus status);

    long countByTrip_IdAndStatus(Long tripId, MembershipStatus status);

    boolean existsByTrip_IdAndUser_IdAndStatus(Long tripId, Long userId, MembershipStatus status);

    @EntityGraph(attributePaths = {"user", "trip"})
    Optional<TripMemberEntity> findByTrip_IdAndUser_IdAndStatus(Long tripId, Long userId, MembershipStatus status);

}
