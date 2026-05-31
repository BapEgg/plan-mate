package com.planmate.auth.dto;

import com.planmate.user.domain.UserAccount;
import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;

public record SignupResponse(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status
) {

    public static SignupResponse from(UserAccount userAccount) {
        return new SignupResponse(
                userAccount.id(),
                userAccount.email(),
                userAccount.nickname(),
                userAccount.role(),
                userAccount.status()
        );
    }

}
