package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/password/change} (인증 필요) — Phase 10 P0-2. */
public record PasswordChangeRequest(
        @NotBlank @Size(min = 1, max = 100) String oldPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}
