package com.planmate.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginIdRecoveryConfirmRequest(
        @NotBlank
        String token
) {
}
