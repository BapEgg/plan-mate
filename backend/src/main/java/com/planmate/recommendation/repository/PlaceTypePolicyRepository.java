package com.planmate.recommendation.repository;

import com.planmate.recommendation.entity.PlaceTypePolicyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceTypePolicyRepository extends JpaRepository<PlaceTypePolicyEntity, Long> {

    List<PlaceTypePolicyEntity> findByEnabledTrue();
}
