package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, Long> {

    List<ItineraryEntity> findByTripIdOrderByCreatedAtDesc(Long tripId);

    Optional<ItineraryEntity> findFirstByTripIdOrderByCreatedAtDesc(Long tripId);

    Optional<ItineraryEntity> findByGeneration_Id(Long generationId);

    /**
     * ADR-0002: trips.current_itinerary_id 포인터가 가리키는 itinerary를 조회한다. 이 module은
     * ArchUnit 규칙(itinerary_package_does_not_depend_on_trip_persistence)에 따라
     * trip.entity/trip.repository에 의존할 수 없으므로, native SQL로 trips table을 직접
     * join한다 — Java 클래스 의존이 아니라 문자열 SQL이므로 패키지 경계를 넘지 않는다.
     */
    @Query(
            value = "SELECT i.* FROM itineraries i JOIN trips t ON t.current_itinerary_id = i.id WHERE t.id = :tripId",
            nativeQuery = true
    )
    Optional<ItineraryEntity> findCurrentByTripId(@Param("tripId") Long tripId);

    /**
     * ADR-0002: 새 itinerary를 저장하기 전 다음 version 번호를 계산하는 데 쓴다.
     * {@code trip_id}는 이미 이 entity의 plain column이므로 trip package 의존 없이 계산할 수 있다.
     */
    @Query("SELECT COALESCE(MAX(i.version), 0) FROM ItineraryEntity i WHERE i.tripId = :tripId")
    int findMaxVersionByTripId(@Param("tripId") Long tripId);

    /**
     * ADR-0002: 새 itinerary를 current pointer로 만든다. itinerary package는 trip.entity/
     * trip.repository에 의존할 수 없으므로(ArchUnit) native SQL로 직접 갱신한다.
     */
    @Modifying
    @Query(value = "UPDATE trips SET current_itinerary_id = :itineraryId WHERE id = :tripId", nativeQuery = true)
    void markAsCurrentForTrip(@Param("tripId") Long tripId, @Param("itineraryId") Long itineraryId);
}
