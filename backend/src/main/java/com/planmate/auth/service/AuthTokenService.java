package com.planmate.auth.service;

import com.planmate.auth.exception.InvalidAuthTokenException;
import com.planmate.auth.repository.RefreshTokenStore;
import com.planmate.auth.security.JwtToken;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.auth.security.OpaqueTokenService;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {

    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OpaqueTokenService opaqueTokenService;
    private final Duration refreshTokenTtl;

    public AuthTokenService(
            RefreshTokenStore refreshTokenStore,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            OpaqueTokenService opaqueTokenService,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.refreshTokenStore = refreshTokenStore;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.opaqueTokenService = opaqueTokenService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthTokenBundle issueTokens(UserEntity user) {
        Instant now = Instant.now();
        JwtToken accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole(), now);
        String rawRefreshToken = opaqueTokenService.generateRawToken();
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);

        refreshTokenStore.save(opaqueTokenService.hash(rawRefreshToken), user.getId(), refreshTokenTtl);

        return new AuthTokenBundle(accessToken, rawRefreshToken, refreshTokenTtl, refreshExpiresAt);
    }

    @Transactional(readOnly = true)
    public JwtToken refreshAccessToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidAuthTokenException();
        }

        Instant now = Instant.now();
        Long userId = refreshTokenStore.findUserIdByTokenHash(opaqueTokenService.hash(rawRefreshToken))
                .orElseThrow(InvalidAuthTokenException::new);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(InvalidAuthTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAuthTokenException();
        }

        return jwtTokenProvider.issueAccessToken(user.getId(), user.getRole(), now);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenStore.delete(opaqueTokenService.hash(rawRefreshToken));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenStore.deleteAllByUserId(userId);
    }

}
