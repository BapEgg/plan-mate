package com.planmate.trip.repository;

import com.planmate.trip.entity.TripEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<TripEntity, Long> {

    @Query("""
            SELECT trip
            FROM TripEntity trip
            JOIN TripMemberEntity member ON member.trip = trip
            WHERE trip.id = :tripId
              AND member.user.id = :userId
            """)
    Optional<TripEntity> findAccessibleTrip(@Param("tripId") Long tripId, @Param("userId") Long userId);

}
