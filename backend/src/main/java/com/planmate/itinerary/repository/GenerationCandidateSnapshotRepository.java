package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationCandidateSnapshotRepository extends JpaRepository<GenerationCandidateSnapshotEntity, Long> {

    List<GenerationCandidateSnapshotEntity> findByGeneration_IdOrderByRankAsc(Long generationId);

    @Query("""
            SELECT candidate
            FROM GenerationCandidateSnapshotEntity candidate
            WHERE candidate.generation.tripId = :tripId
              AND candidate.placeId IN :placeIds
            ORDER BY candidate.generation.createdAt DESC, candidate.rank ASC
            """)
    List<GenerationCandidateSnapshotEntity> findLatestTripSnapshots(
            @Param("tripId") Long tripId,
            @Param("placeIds") Collection<String> placeIds
    );

    long countByGeneration_Id(Long generationId);

    void deleteByGeneration_Id(Long generationId);
}
