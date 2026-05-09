package com.samhanair.logis.user.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 직원 역할 변경 이력 — Phase 10 P0-5.
 *
 * <p>{@code PATCH /users/employees/{id}/role} 호출 시 본 entity 1행 추가 + Employee.roleSnapshot 갱신.
 * 매뉴얼 §4 (변경 이력) 화면의 backing data.
 *
 * <p>BaseEntity 7 audit + Soft Delete 의무. {@code created_at} 가 변경 시각이며,
 * {@code created_by} = 변경자 userId.
 */
@Entity
@Getter
@Table(name = "role_change_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class RoleChangeHistory extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", length = 20)
    private Role previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false, length = 20)
    private Role newRole;

    @Column(name = "reason", length = 500)
    private String reason;

    private RoleChangeHistory(UUID employeeId, Role previousRole, Role newRole, String reason) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId 필수");
        }
        if (newRole == null) {
            throw new IllegalArgumentException("newRole 필수");
        }
        this.employeeId = employeeId;
        this.previousRole = previousRole;
        this.newRole = newRole;
        this.reason = reason;
    }

    /**
     * 신규 이력 행 생성.
     *
     * @param employeeId 대상 직원 UUID
     * @param previousRole 변경 전 역할 (신규 직원이면 null 가능)
     * @param newRole 변경 후 역할 (필수)
     * @param reason 변경 사유 (옵션)
     */
    public static RoleChangeHistory record(UUID employeeId, Role previousRole, Role newRole, String reason) {
        return new RoleChangeHistory(employeeId, previousRole, newRole, reason);
    }
}
