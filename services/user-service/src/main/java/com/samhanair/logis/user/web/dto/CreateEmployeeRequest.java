package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Body of {@code POST /users/employees}. */
public record CreateEmployeeRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String fullName,
        @NotBlank @Size(max = 30) String position,
        @NotNull Role role,
        @NotNull UUID departmentId,
        boolean teamLead,
        @NotNull LocalDate hireDate,
        @Size(max = 100) String email,
        @Size(max = 20) String phone) {
}
