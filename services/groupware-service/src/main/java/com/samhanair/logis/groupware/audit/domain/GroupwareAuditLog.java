package com.samhanair.logis.groupware.audit.domain;

import com.samhanair.logis.shared.realtime.audit.AuditLogEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * Groupware 도메인 audit overlay — PR-H4b (Phase 12 Step 4b) BE-E.
 *
 * <p>{@link AuditLogEntry} (shared:realtime-abstraction @MappedSuperclass) 를 상속하여 9 필드 +
 * BaseEntity 7 audit 필드를 자동 보유. 본 entity 는 id 컬럼 + entity_id 의미 alias 만 추가.
 *
 * <p><b>entity_id 의미</b>: ApprovalLine.id / Message.id / Schedule.id 등 groupware 도메인 entity
 * UUID. 어느 도메인 entity 인지는 호출자 서비스가 fieldName prefix (예: "approval.title",
 * "message.body", "schedule.startDate") 로 구분 가능. 향후 PR 에서 별도 entityType 컬럼 추가
 * 검토.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출 식별자
 * = actorName 만. actorId (UUID) 는 audit/감사 추적용.
 *
 * <p><b>Soft-delete</b>: 회계 감사 / 분쟁 대응 — 본 row 는 BaseEntity.markDeleted 로만 비활성.
 * 실 DELETE 금지.
 */
@Entity
@Getter
@Table(name = "groupware_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class GroupwareAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 audit log 정적 factory — shared {@link AuditLogEntry#init} 위임.
     *
     * @param entityId 소속 도메인 entity UUID (ApprovalLine.id / Message.id / Schedule.id)
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID (audit/감사 추적용)
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자, 도메인 prefix 권장)
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 거부)
     * @return 영속화 전 신규 GroupwareAuditLog
     */
    public static GroupwareAuditLog record(UUID entityId, int revisionNo, UUID actorId,
                                           String actorName, String actorColor,
                                           String fieldName, String oldValue, String newValue) {
        GroupwareAuditLog log = new GroupwareAuditLog();
        log.init(entityId, revisionNo, actorId, actorName, actorColor, fieldName,
                oldValue, newValue, LocalDateTime.now());
        return log;
    }
}
