package com.samhanair.logis.partnerorder.audit.domain;

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
 * 거래처 주문 본문 수정 audit overlay — PR-H4b (Phase 12 Step 4b BE-C).
 *
 * <p>{@link AuditLogEntry} ({@code shared:realtime-abstraction}) 의 {@code @MappedSuperclass}
 * 상속으로 entity_id / revision_no / actor_* / field_name / old/new value / changed_at + BaseEntity 7
 * 자동 보유.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출 식별자
 * = {@code actorName} 만. {@code actorId} 는 audit/감사 추적용.
 *
 * <p><b>Soft-delete</b>: 회계 감사 / 분쟁 대응 — 본 row 는 BaseEntity.markDeleted 로만 비활성.
 *
 * <p><b>FK 미강제</b>: PartnerOrder soft delete 후에도 audit row 보존.
 */
@Entity
@Getter
@Table(name = "partner_order_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerOrderAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 audit log 정적 factory.
     *
     * @param partnerOrderId 소속 PartnerOrder UUID (entity_id 컬럼)
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 은 거부)
     * @return 영속화 전 신규 PartnerOrderAuditLog
     */
    public static PartnerOrderAuditLog record(UUID partnerOrderId, int revisionNo, UUID actorId,
                                              String actorName, String actorColor, String fieldName,
                                              String oldValue, String newValue) {
        PartnerOrderAuditLog row = new PartnerOrderAuditLog();
        row.init(partnerOrderId, revisionNo, actorId, actorName, actorColor, fieldName,
                oldValue, newValue, LocalDateTime.now());
        return row;
    }

    /** entity_id alias — partnerOrderId 의 의미 명시 (UUID 비공개 가드 — admin 작업용). */
    public UUID getPartnerOrderId() {
        return getEntityId();
    }
}
