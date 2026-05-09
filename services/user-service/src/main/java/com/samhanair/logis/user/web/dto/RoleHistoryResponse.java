package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.RoleChangeHistory;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 역할 변경 이력 응답 — Phase 10 P0-5.
 *
 * <p>{@code GET /users/employees/{id}/role-history} 응답 1행. 매뉴얼 §4 변경 이력 탭 표시용.
 *
 * @param id 이력 행 UUID
 * @param previousRole 변경 전 (신규 직원이면 null)
 * @param newRole 변경 후
 * @param reason 변경 사유 (옵션)
 * @param changedAt 변경 시각 (created_at)
 * @param changedBy 변경자 userId (created_by)
 */
public record RoleHistoryResponse(
        UUID id,
        Role previousRole,
        Role newRole,
        String reason,
        LocalDateTime changedAt,
        String changedBy
) {

    public static RoleHistoryResponse from(RoleChangeHistory h) {
        return new RoleHistoryResponse(
                h.getId(),
                h.getPreviousRole(),
                h.getNewRole(),
                h.getReason(),
                h.getCreatedAt(),
                h.getCreatedBy());
    }
}
