package com.samhanair.logis.arologis.realtime.domain;

import com.samhanair.logis.shared.realtime.audit.AuditLogEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * arologis 도메인 audit overlay — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link AuditLogEntry} ({@code shared:realtime-abstraction} @MappedSuperclass) 를 상속.
 * {@code entity_id} 의미 = 변경 대상 entity (Dispatch / VehicleStop) UUID.
 *
 * <p><b>UUID 비공개 가드</b>: 사용자 화면 노출 식별자 = actorName 만.
 *
 * <p><b>Soft-delete</b>: 회계 감사 — markDeleted 만.
 */
@Entity
@Table(name = "arologis_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ArologisAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    public UUID getId() {
        return id;
    }

    /**
     * 신규 audit log 정적 factory.
     */
    public static ArologisAuditLog record(UUID entityId, int revisionNo, UUID actorId,
                                          String actorName, String actorColor, String fieldName,
                                          String oldValue, String newValue) {
        ArologisAuditLog log = new ArologisAuditLog();
        log.init(entityId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue, LocalDateTime.now());
        return log;
    }
}
