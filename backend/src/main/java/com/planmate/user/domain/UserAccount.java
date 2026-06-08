package com.planmate.user.domain;

public record UserAccount(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status,
        boolean emailVerified
) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

}
