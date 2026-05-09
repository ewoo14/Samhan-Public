package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/password/reset/request} — Phase 10 P0-2. */
public record PasswordResetRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Email @Size(max = 255) String email) {
}
