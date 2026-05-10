package com.samhanair.logis.accounting.audit.repository;

import com.samhanair.logis.accounting.audit.domain.AccountingAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회계 도메인 audit overlay log — entityId 기반 조회 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>soft-delete 자동 제외 ({@code @SQLRestriction}). FE timeline UI 는
 * {@link #findByEntityIdOrderByRevisionNoDescChangedAtDesc} 결과를 그대로 표시 (최신 revision
 * 우선). UUID 비공개 가드 — 응답 actorId 는 FE 색상 hash 결정성용, 화면 표시는 actorName.
 */
public interface AccountingAuditLogRepository extends JpaRepository<AccountingAuditLog, UUID> {

    /** entity 별 audit log — 최신 revision 우선 (FE timeline 기본 정렬). */
    List<AccountingAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** 특정 entity + revision 의 audit row (다중 필드 변경 시 N row). */
    List<AccountingAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);
}
