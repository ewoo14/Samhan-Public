package com.samhanair.logis.user.audit.domain;

import com.samhanair.logis.shared.realtime.audit.AuditLogEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * user-service audit overlay log — PR-H4b (Phase 12 Step 4b).
 *
 * <p>Employee / Department 변경 1건당 필드별 1행. shared:realtime-abstraction 의
 * {@link AuditLogEntry} {@code @MappedSuperclass} 를 상속하여 9 audit 필드 + BaseEntity 7 audit
 * 자동 보유.
 *
 * <p><b>UUID 비공개 가드</b>: 사용자 화면 노출 식별자 = {@code actorName} 만. {@code actorId} 는
 * 감사 추적용. {@code entityId} = Employee.id 또는 Department.id (FK 미강제).
 *
 * <p><b>Soft-delete</b>: 인사 기록 영구 보존. BaseEntity.markDeleted 로만 비활성.
 */
@Entity
@Getter
@Table(name = "user_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class UserAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private UserAuditLog(UUID entityId, int revisionNo, UUID actorId, String actorName,
                         String actorColor, String fieldName, String oldValue, String newValue) {
        init(entityId, revisionNo, actorId, actorName, actorColor, fieldName, oldValue, newValue, null);
    }

    /**
     * 신규 audit log 정적 factory — Employee / Department 변경 시 호출.
     *
     * @param entityId Employee.id 또는 Department.id
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 거부)
     * @return 영속화 전 신규 UserAuditLog
     */
    public static UserAuditLog record(UUID entityId, int revisionNo, UUID actorId, String actorName,
                                      String actorColor, String fieldName,
                                      String oldValue, String newValue) {
        return new UserAuditLog(entityId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue);
    }
}
