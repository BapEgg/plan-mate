package com.planmate.auth.service;

import com.planmate.auth.dto.LoginResponse;
import java.time.Duration;

public record LoginResult(
        LoginResponse response,
        String refreshToken,
        Duration refreshTokenTtl
) {
}
