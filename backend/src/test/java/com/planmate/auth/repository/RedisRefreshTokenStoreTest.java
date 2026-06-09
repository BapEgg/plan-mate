package com.planmate.auth.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.planmate.auth.exception.RefreshTokenStoreUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRefreshTokenStoreTest {

    @Test
    void wrapsRedisFailureWithPlanMateException() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);

        when(redisTemplate.opsForValue()).thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThatThrownBy(() -> store.findUserIdByTokenHash("token-hash"))
                .isInstanceOf(RefreshTokenStoreUnavailableException.class)
                .hasMessage("Refresh token store is unavailable.");
    }

}
