package com.planmate.trip.repository;

import com.planmate.trip.entity.TripEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<TripEntity, Long> {

    Optional<TripEntity> findByIdAndCreatedBy_Id(Long tripId, Long userId);

    /**
     * WP-B: 여행방 정원(ACTIVE + PENDING invite ≤ 20)처럼 여러 table에 걸친 카운트를 검사·갱신하는
     * command가 같은 trip에 대해 동시에 실행되지 않도록 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT trip FROM TripEntity trip WHERE trip.id = :tripId")
    Optional<TripEntity> findByIdForUpdate(@Param("tripId") Long tripId);

    @Query("""
            SELECT trip
            FROM TripEntity trip
            JOIN TripMemberEntity member ON member.trip = trip
            WHERE trip.id = :tripId
              AND member.user.id = :userId
              AND member.status = com.planmate.trip.entity.MembershipStatus.ACTIVE
            """)
    Optional<TripEntity> findAccessibleTrip(@Param("tripId") Long tripId, @Param("userId") Long userId);

    /**
     * ADR-0002: current itinerary pointer의 낙관적(conditional) 갱신. 영향받은 행이 0이면
     * base가 이미 stale이라는 뜻이며 호출자는 409로 거절해야 한다. WP-A는 이 메서드와 contract
     * test만 제공하고, 실제 호출자(proposal/vote apply, 재생성)는 WP-E/WP-F가 구현한다.
     */
    @Modifying
    @Query("""
            UPDATE TripEntity trip
            SET trip.currentItineraryId = :newItineraryId
            WHERE trip.id = :tripId
              AND (
                  trip.currentItineraryId = :expectedCurrentItineraryId
                  OR (:expectedCurrentItineraryId IS NULL AND trip.currentItineraryId IS NULL)
              )
            """)
    int updateCurrentItineraryIdIfMatches(
            @Param("tripId") Long tripId,
            @Param("expectedCurrentItineraryId") Long expectedCurrentItineraryId,
            @Param("newItineraryId") Long newItineraryId
    );

}
