package com.planmate.auth.service;

import com.planmate.auth.entity.RefreshTokenEntity;
import com.planmate.auth.exception.InvalidAuthTokenException;
import com.planmate.auth.repository.RefreshTokenRepository;
import com.planmate.auth.security.JwtToken;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.auth.security.OpaqueTokenService;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OpaqueTokenService opaqueTokenService;
    private final Duration refreshTokenTtl;

    public AuthTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            OpaqueTokenService opaqueTokenService,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
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

        refreshTokenRepository.save(RefreshTokenEntity.create(
                user,
                opaqueTokenService.hash(rawRefreshToken),
                refreshExpiresAt
        ));

        return new AuthTokenBundle(accessToken, rawRefreshToken, refreshTokenTtl, refreshExpiresAt);
    }

    @Transactional
    public JwtToken refreshAccessToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidAuthTokenException();
        }

        Instant now = Instant.now();
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(opaqueTokenService.hash(rawRefreshToken))
                .orElseThrow(InvalidAuthTokenException::new);

        if (!refreshToken.isUsable(now)) {
            throw new InvalidAuthTokenException();
        }

        UserEntity user = refreshToken.getUser();
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

        refreshTokenRepository.findByTokenHash(opaqueTokenService.hash(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

}
