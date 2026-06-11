package com.planmate.auth.oauth;

import java.util.Locale;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public enum OauthProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static OauthProvider fromRegistrationId(String registrationId) {
        try {
            return OauthProvider.valueOf(registrationId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw oauth2Exception("Unsupported OAuth2 provider: " + registrationId);
        }
    }

    static OAuth2AuthenticationException oauth2Exception(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("oauth2_profile_invalid"), message);
    }

}
