package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Employee;
import java.time.LocalDate;
import java.util.UUID;

/** Full employee view returned by single-fetch + create / update endpoints. */
public record EmployeeResponse(
        UUID id,
        String loginId,
        String fullName,
        String position,
        Role role,
        UUID departmentId,
        String departmentName,
        boolean teamLead,
        LocalDate hireDate,
        LocalDate terminationDate,
        String email,
        String phone) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getLoginId(),
                e.getFullName(),
                e.getPosition(),
                e.getRoleSnapshot(),
                e.getDepartment().getId(),
                e.getDepartment().getName(),
                e.isTeamLead(),
                e.getHireDate(),
                e.getTerminationDate(),
                e.getEmail(),
                e.getPhone());
    }
}
