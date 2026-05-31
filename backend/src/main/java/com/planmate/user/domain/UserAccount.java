package com.planmate.user.domain;

public record UserAccount(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status
) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

}
