package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationCandidateSnapshotRepository extends JpaRepository<GenerationCandidateSnapshotEntity, Long> {

    List<GenerationCandidateSnapshotEntity> findByGeneration_IdOrderByRankAsc(Long generationId);

    long countByGeneration_Id(Long generationId);

    void deleteByGeneration_Id(Long generationId);
}
