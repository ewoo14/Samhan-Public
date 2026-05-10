package com.samhanair.logis.product.audit.repository;

import com.samhanair.logis.product.audit.domain.ProductAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 제품 audit overlay log — entity_id (= Product.id) 기반 조회.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 */
public interface ProductAuditLogRepository extends JpaRepository<ProductAuditLog, UUID> {

    /** 제품별 audit log — 최신 revision 우선. */
    List<ProductAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** 특정 제품 + revision 의 audit row. revert 시 snapshot 소스. */
    List<ProductAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);
}
