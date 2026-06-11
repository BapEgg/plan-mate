package com.planmate.auth.oauth;

public record OauthUserProfile(
        OauthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname
) {
}
