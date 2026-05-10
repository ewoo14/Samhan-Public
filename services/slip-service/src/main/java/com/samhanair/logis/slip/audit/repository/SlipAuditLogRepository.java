package com.samhanair.logis.slip.audit.repository;

import com.samhanair.logis.slip.audit.domain.SlipAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 슬립 audit overlay log — slipId 기반 조회. soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>FE timeline UI 는 {@link #findBySlipIdOrderByRevisionNoDescChangedAtDesc} 결과를 그대로
 * 표시 (최신 revision 우선 + 같은 revision 내 같은 시각 정렬). revert 는 service 레이어에서
 * {@link #findBySlipIdAndRevisionNo} 로 특정 revision 의 row 들 조회.
 */
public interface SlipAuditLogRepository extends JpaRepository<SlipAuditLog, UUID> {

    /** 슬립별 audit log — 최신 revision 우선 (FE timeline 기본 정렬). */
    List<SlipAuditLog> findBySlipIdOrderByRevisionNoDescChangedAtDesc(UUID slipId);

    /** 특정 슬립 + revision 의 audit row (다중 필드 변경 시 N row). revert 시 snapshot 소스. */
    List<SlipAuditLog> findBySlipIdAndRevisionNo(UUID slipId, int revisionNo);
}
