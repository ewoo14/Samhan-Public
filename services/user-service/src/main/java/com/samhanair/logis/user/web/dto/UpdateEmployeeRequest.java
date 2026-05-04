package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of {@code PATCH /users/employees/{id}}. All fields nullable — only non-null
 * fields are applied. Role changes are NOT allowed here (use {@code PATCH .../role}).
 */
public record UpdateEmployeeRequest(
        @Size(max = 50) String fullName,
        @Size(max = 30) String position,
        UUID departmentId,
        Boolean teamLead,
        @Size(max = 100) String email,
        @Size(max = 20) String phone) {
}
