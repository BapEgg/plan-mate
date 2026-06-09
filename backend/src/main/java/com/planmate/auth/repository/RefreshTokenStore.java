package com.planmate.auth.repository;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(String tokenHash, Long userId, Duration ttl);

    Optional<Long> findUserIdByTokenHash(String tokenHash);

    void delete(String tokenHash);

    void deleteAllByUserId(Long userId);

}
