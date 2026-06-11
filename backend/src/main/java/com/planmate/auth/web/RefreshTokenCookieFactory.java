package com.planmate.auth.web;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    public ResponseCookie create(String refreshToken, Duration maxAge) {
        return baseCookie(refreshToken)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth");
    }

}
