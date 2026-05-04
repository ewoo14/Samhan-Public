package com.samhanair.logis.auth.web.dto;

import com.samhanair.logis.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/register} (MASTER-only). */
public record RegisterRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull Role role) {
}
