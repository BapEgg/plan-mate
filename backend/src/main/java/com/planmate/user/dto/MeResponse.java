package com.planmate.user.dto;

import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;
import java.util.List;

public record MeResponse(
        Long id,
        String loginId,
        String email,
        String nickname,
        String profileImageUrl,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        List<String> linkedProviders
) {
}
