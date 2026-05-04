package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.user.domain.Department;
import java.util.UUID;

/** Department view returned by {@code GET /users/departments}. */
public record DepartmentResponse(
        UUID id,
        String code,
        String name,
        int displayOrder) {

    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getDisplayOrder());
    }
}
