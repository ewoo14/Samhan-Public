package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/login}. */
public record LoginRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
