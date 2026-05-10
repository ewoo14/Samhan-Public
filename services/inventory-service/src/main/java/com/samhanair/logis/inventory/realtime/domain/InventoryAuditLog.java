package com.samhanair.logis.inventory.realtime.domain;

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
 * inventory 도메인 audit overlay — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link AuditLogEntry} ({@code shared:realtime-abstraction} @MappedSuperclass) 를 상속하여
 * BaseEntity 7 audit + audit overlay 9 필드 자동 보유. 컬럼명 entity_id 유지 (shared 일관).
 *
 * <p>{@code entity_id} 의미 = 변경 대상 entity (InventoryAudit / StockBalance / StockLot 등) UUID.
 *
 * <p><b>UUID 비공개 가드</b>: 사용자 화면 노출 식별자 = actorName 만.
 *
 * <p><b>Soft-delete</b>: 회계 감사 — markDeleted 만 (실 DELETE 금지).
 */
@Entity
@Table(name = "inventory_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class InventoryAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    public UUID getId() {
        return id;
    }

    /**
     * 신규 audit log 정적 factory.
     *
     * @param entityId 변경 대상 entity UUID (InventoryAudit/StockBalance/StockLot 등)
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 은 거부)
     * @return 영속화 전 신규 InventoryAuditLog
     */
    public static InventoryAuditLog record(UUID entityId, int revisionNo, UUID actorId,
                                           String actorName, String actorColor, String fieldName,
                                           String oldValue, String newValue) {
        InventoryAuditLog log = new InventoryAuditLog();
        log.init(entityId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue, LocalDateTime.now());
        return log;
    }
}
