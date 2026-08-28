package com.planmate.trip.repository;

import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface TripPlanningProfileRepository extends JpaRepository<TripPlanningProfileEntity, Long> {

    Optional<TripPlanningProfileEntity> findByTrip_Id(Long tripId);

    @EntityGraph(attributePaths = {"trip"})
    Optional<TripPlanningProfileEntity> findWithTripByTrip_Id(Long tripId);
}
