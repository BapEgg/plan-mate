package com.planmate.trip.repository;

import com.planmate.trip.entity.TripMemberEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMemberEntity, Long> {

    @EntityGraph(attributePaths = {"trip"})
    List<TripMemberEntity> findByUser_IdOrderByTrip_CreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<TripMemberEntity> findByTrip_IdOrderByCreatedAtAsc(Long tripId);

    long countByTrip_Id(Long tripId);

    boolean existsByTrip_IdAndUser_Id(Long tripId, Long userId);

}
