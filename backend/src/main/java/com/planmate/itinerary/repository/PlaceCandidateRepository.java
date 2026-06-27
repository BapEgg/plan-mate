package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.PlaceCandidateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidateEntity, Long> {

    List<PlaceCandidateEntity> findByGeneration_IdOrderByRankAsc(Long generationId);

    long countByGeneration_Id(Long generationId);
}
