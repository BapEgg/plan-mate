package com.planmate.auth.repository;

import com.planmate.auth.entity.LocalCredentialEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredentialEntity, Long> {

    boolean existsByLoginId(String loginId);

    Optional<LocalCredentialEntity> findByLoginId(String loginId);

    Optional<LocalCredentialEntity> findByUserId(Long userId);

}
