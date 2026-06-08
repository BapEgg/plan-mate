package com.planmate.auth.repository;

import com.planmate.auth.entity.OauthAccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccountEntity, Long> {

    List<OauthAccountEntity> findByUserId(Long userId);

}
