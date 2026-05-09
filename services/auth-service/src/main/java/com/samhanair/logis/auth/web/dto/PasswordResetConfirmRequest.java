package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/password/reset/confirm} — Phase 10 P0-2. */
public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 255) String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}
