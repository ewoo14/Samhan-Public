package com.samhanair.logis.auth.web.dto.internal;

import com.samhanair.logis.common.security.Role;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /auth/internal/accounts/{id}/role}. */
public record UpdateRoleInternalRequest(@NotNull Role role) {
}
