package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.common.security.Role;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /users/employees/{id}/role}. */
public record UpdateRoleRequest(@NotNull Role role) {
}
