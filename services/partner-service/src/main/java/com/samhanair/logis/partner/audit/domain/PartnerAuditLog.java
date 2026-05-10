package com.samhanair.logis.partner.audit.domain;

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
 * 거래처 도메인 audit overlay — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared:realtime-abstraction 의 {@link AuditLogEntry} @MappedSuperclass 상속 — entity 9 필드
 * + BaseEntity 7 audit 필드 자동 보유. partner-service 의 모든 도메인 (Partner / BlockedPartner)
 * mutation 시 1행 INSERT.
 *
 * <p><b>entity_id 의미</b> — Partner / BlockedPartner UUID. service layer 가 entity_kind 별도
 * 인지하지 않고 field_name prefix ("partner.name" / "blocked.reason" 등) 로 도메인 식별.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출 식별자
 * = actorName 만. actorId 는 audit/감사 추적용.
 */
@Entity
@Getter
@Table(name = "partner_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 audit log 정적 factory — shared {@link AuditLogEntry#init} 위임.
     *
     * @param entityId 대상 entity (Partner / BlockedPartner) UUID
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자, 권장 prefix: "partner." / "blocked.")
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 은 거부)
     * @return 영속화 전 신규 PartnerAuditLog
     */
    public static PartnerAuditLog record(UUID entityId, int revisionNo, UUID actorId,
                                         String actorName, String actorColor, String fieldName,
                                         String oldValue, String newValue) {
        PartnerAuditLog log = new PartnerAuditLog();
        log.init(entityId, revisionNo, actorId, actorName, actorColor, fieldName,
                oldValue, newValue, LocalDateTime.now());
        return log;
    }
}
