package com.planmate.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank
        @Size(min = 2, max = 30)
        String nickname
) {
}
