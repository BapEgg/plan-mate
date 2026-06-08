package com.planmate.auth.security;

import java.time.Instant;

public record JwtToken(
        String value,
        long expiresInSeconds,
        Instant expiresAt
) {
}
