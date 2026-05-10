package com.samhanair.logis.inventory.realtime.repository;

import com.samhanair.logis.inventory.realtime.domain.InventoryAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * inventory audit overlay repository — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared {@link com.samhanair.logis.shared.realtime.audit.AuditLogEntry} 의 entity_id 컬럼 기반
 * lookup. Spring Data JPA 메서드명 derivation 으로 자동 생성.
 */
public interface InventoryAuditLogRepository extends JpaRepository<InventoryAuditLog, UUID> {

    /** entity 별 audit log 전체 — 최신 revision 우선 (FE timeline 표시). */
    List<InventoryAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** revert 시 특정 revision 의 row 들 lookup. */
    List<InventoryAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);

    /** entity 별 최대 revision_no — 다음 채번용 (entity 가 revisionCount 컬럼 미보유 환경). */
    long countByEntityId(UUID entityId);
}
