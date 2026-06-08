package com.planmate.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static TokenRefreshResponse bearer(String accessToken, long expiresIn) {
        return new TokenRefreshResponse(accessToken, "Bearer", expiresIn);
    }

}
