package com.planmate.auth.repository;

import com.planmate.auth.entity.AuthEmailTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEmailTokenRepository extends JpaRepository<AuthEmailTokenEntity, Long> {

    Optional<AuthEmailTokenEntity> findByTokenHash(String tokenHash);

}
