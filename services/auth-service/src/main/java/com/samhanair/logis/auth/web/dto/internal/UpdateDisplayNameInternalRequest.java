package com.samhanair.logis.auth.web.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /auth/internal/accounts/{id}/display-name} (Q2 propagation). */
public record UpdateDisplayNameInternalRequest(
        @NotBlank @Size(max = 100) String displayName) {
}
