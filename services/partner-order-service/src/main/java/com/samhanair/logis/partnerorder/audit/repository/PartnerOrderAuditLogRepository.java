package com.samhanair.logis.partnerorder.audit.repository;

import com.samhanair.logis.partnerorder.audit.domain.PartnerOrderAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래처 주문 audit overlay log — entity_id (= PartnerOrder.id) 기반 조회.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>FE timeline UI 는 {@link #findByEntityIdOrderByRevisionNoDescChangedAtDesc} 결과를 그대로
 * 표시 (최신 revision 우선). revert 는 service 가 {@link #findByEntityIdAndRevisionNo} 호출.
 */
public interface PartnerOrderAuditLogRepository extends JpaRepository<PartnerOrderAuditLog, UUID> {

    /** 주문별 audit log — 최신 revision 우선 (FE timeline 기본 정렬). */
    List<PartnerOrderAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** 특정 주문 + revision 의 audit row (다중 필드 변경 시 N row). revert 시 snapshot 소스. */
    List<PartnerOrderAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);
}
