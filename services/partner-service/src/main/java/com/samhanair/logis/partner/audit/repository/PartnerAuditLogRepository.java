package com.samhanair.logis.partner.audit.repository;

import com.samhanair.logis.partner.audit.domain.PartnerAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래처 도메인 audit overlay log — entityId 기반 조회 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>soft-delete 자동 제외 ({@code @SQLRestriction}). FE timeline UI 는
 * {@link #findByEntityIdOrderByRevisionNoDescChangedAtDesc} 결과를 그대로 표시.
 */
public interface PartnerAuditLogRepository extends JpaRepository<PartnerAuditLog, UUID> {

    List<PartnerAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    List<PartnerAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);
}
