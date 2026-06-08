package com.planmate.auth.dto;

public record AuthStatusResponse(
        boolean authenticated,
        AuthUserSummary user
) {

    public static AuthStatusResponse anonymous() {
        return new AuthStatusResponse(false, null);
    }

    public static AuthStatusResponse authenticated(AuthUserSummary user) {
        return new AuthStatusResponse(true, user);
    }

    public record AuthUserSummary(
            Long id,
            String loginId,
            String nickname,
            String role
    ) {
    }

}
