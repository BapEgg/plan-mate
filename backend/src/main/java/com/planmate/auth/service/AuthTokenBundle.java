package com.planmate.auth.service;

import com.planmate.auth.security.JwtToken;
import java.time.Duration;
import java.time.Instant;

public record AuthTokenBundle(
        JwtToken accessToken,
        String refreshToken,
        Duration refreshTokenTtl,
        Instant refreshTokenExpiresAt
) {
}
