package com.planmate.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank
        String loginId,

        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}
