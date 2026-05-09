package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.common.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /users/employees/{id}/role}.
 *
 * <p>Phase 10 P0-5 — {@code reason} 옵션 추가. 입력 시 {@code RoleChangeHistory.reason} 에 영속화.
 */
public record UpdateRoleRequest(
        @NotNull Role role,
        @Size(max = 500) String reason) {

    /** 호환 — reason 미입력 호출 (기존 호출자 비파괴). */
    public UpdateRoleRequest(Role role) {
        this(role, null);
    }
}
