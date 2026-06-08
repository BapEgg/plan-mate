package com.planmate.auth.repository;

import com.planmate.auth.entity.AuthEmailLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEmailLogRepository extends JpaRepository<AuthEmailLogEntity, Long> {
}
