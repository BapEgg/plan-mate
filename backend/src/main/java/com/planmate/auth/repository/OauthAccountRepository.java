package com.planmate.auth.repository;

import com.planmate.auth.entity.OauthAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccountEntity, Long> {

    List<OauthAccountEntity> findByUserId(Long userId);

    Optional<OauthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

}
