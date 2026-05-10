package com.samhanair.logis.dcconfig.audit.domain;

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
 * dc-config-service audit overlay log — PR-H4b (Phase 12 Step 4b).
 *
 * <p>DcConfig 16종 CFG_RAW (할인율/옵션 정액/단위 반올림 등) 변경 1건당 필드별 1행. shared
 * module 의 {@link AuditLogEntry} {@code @MappedSuperclass} 상속 → BaseEntity 7 audit + 9 audit
 * 필드 자동 보유.
 *
 * <p><b>UUID 비공개 가드</b>: actorName 만 사용자 화면 노출.
 * <p><b>Soft-delete</b>: DC 정책 변경 분쟁 대응 — 영구 보존.
 */
@Entity
@Getter
@Table(name = "dc_config_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class DcConfigAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private DcConfigAuditLog(UUID entityId, int revisionNo, UUID actorId, String actorName,
                             String actorColor, String fieldName, String oldValue, String newValue) {
        init(entityId, revisionNo, actorId, actorName, actorColor, fieldName, oldValue, newValue, null);
    }

    /** 신규 audit log 정적 factory. */
    public static DcConfigAuditLog record(UUID entityId, int revisionNo, UUID actorId, String actorName,
                                          String actorColor, String fieldName,
                                          String oldValue, String newValue) {
        return new DcConfigAuditLog(entityId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue);
    }
}
