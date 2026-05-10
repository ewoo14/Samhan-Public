package com.samhanair.logis.notification.audit.repository;

import com.samhanair.logis.notification.audit.domain.NotificationAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * notification-service audit overlay log repository — PR-H4b.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 */
public interface NotificationAuditLogRepository extends JpaRepository<NotificationAuditLog, UUID> {

    /** entity 별 audit log — 최신 revision 우선. */
    List<NotificationAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** 특정 entity + revision 의 audit row. */
    List<NotificationAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);

    /** 다음 revision 채번 — 현재 entity 의 최대 revision_no 조회. */
    @Query("SELECT COALESCE(MAX(a.revisionNo), 0) FROM NotificationAuditLog a WHERE a.entityId = :entityId")
    int findMaxRevisionByEntityId(@Param("entityId") UUID entityId);
}
