package com.samhanair.logis.user.service.dto;

import com.samhanair.logis.common.security.Role;
import java.util.UUID;

/** Lightweight projection used by lookup + org-chart endpoints. */
public record EmployeeProjection(
        UUID id,
        String fullName,
        Role role,
        String departmentName,
        String position) {
}
