package com.planmate.auth.dto;

import com.planmate.user.domain.UserAccount;
import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;

public record AuthUserResponse(
        Long id,
        String loginId,
        String email,
        String nickname,
        UserRole role,
        UserStatus status,
        boolean emailVerified
) {

    public static AuthUserResponse from(UserAccount userAccount, String loginId) {
        return new AuthUserResponse(
                userAccount.id(),
                loginId,
                userAccount.email(),
                userAccount.nickname(),
                userAccount.role(),
                userAccount.status(),
                userAccount.emailVerified()
        );
    }

}
