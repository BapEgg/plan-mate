package com.planmate.auth.repository;

import com.planmate.auth.exception.RefreshTokenStoreUnavailableException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String USER_KEY_PREFIX = "auth:refresh:user:";

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String tokenHash, Long userId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(tokenKey(tokenHash), userId.toString(), ttl);
            redisTemplate.opsForSet().add(userKey(userId), tokenHash);
            redisTemplate.expire(userKey(userId), ttl);
        } catch (DataAccessException exception) {
            throw new RefreshTokenStoreUnavailableException(exception);
        }
    }

    @Override
    public Optional<Long> findUserIdByTokenHash(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().get(tokenKey(tokenHash));
            if (userId == null) {
                return Optional.empty();
            }

            return Optional.of(Long.parseLong(userId));
        } catch (NumberFormatException exception) {
            delete(tokenHash);
            return Optional.empty();
        } catch (DataAccessException exception) {
            throw new RefreshTokenStoreUnavailableException(exception);
        }
    }

    @Override
    public void delete(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().get(tokenKey(tokenHash));
            redisTemplate.delete(tokenKey(tokenHash));
            if (userId != null) {
                redisTemplate.opsForSet().remove(userKey(userId), tokenHash);
            }
        } catch (DataAccessException exception) {
            throw new RefreshTokenStoreUnavailableException(exception);
        }
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        try {
            String userKey = userKey(userId);
            Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
            if (tokenHashes != null && !tokenHashes.isEmpty()) {
                redisTemplate.delete(tokenHashes.stream()
                        .map(this::tokenKey)
                        .toList());
            }
            redisTemplate.delete(userKey);
        } catch (DataAccessException exception) {
            throw new RefreshTokenStoreUnavailableException(exception);
        }
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String userKey(String userId) {
        return USER_KEY_PREFIX + userId;
    }

}
