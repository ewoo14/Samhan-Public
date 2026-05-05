package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.SlipPublishAudit;
import com.samhanair.logis.slip.domain.SlipSourceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Phase 6 M5 (slip-service-integration) 감사 로그 저장소.
 * 회계 reference 영구 보존이므로 mutation API 는 제공하지 않는다 (insert + read only).
 */
public interface SlipPublishAuditRepository extends JpaRepository<SlipPublishAudit, UUID> {

    /** 특정 슬립의 발행 audit 모두 조회 (정상적으로는 1건). */
    List<SlipPublishAudit> findAllBySlipIdAndIsDeletedFalse(UUID slipId);

    /** 같은 출처 (sourceType + sourceId) 의 모든 발행 audit — 회계 cross-check 용. */
    List<SlipPublishAudit> findAllBySourceTypeAndSourceIdAndIsDeletedFalse(
            SlipSourceType sourceType, String sourceId);
}
