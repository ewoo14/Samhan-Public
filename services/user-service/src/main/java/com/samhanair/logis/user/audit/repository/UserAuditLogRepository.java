package com.samhanair.logis.user.audit.repository;

import com.samhanair.logis.user.audit.domain.UserAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user-service audit overlay log repository — entityId (Employee/Department.id) 기반 조회.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>FE timeline UI 는 {@link #findByEntityIdOrderByRevisionNoDescChangedAtDesc} 결과를 그대로
 * 표시 (최신 revision 우선). PR-H4c 50+ page UI 통합 단계에서 controller endpoint 노출 예정.
 */
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, UUID> {

    /** 사용자/부서별 audit log — 최신 revision 우선 (FE timeline 기본 정렬). */
    List<UserAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /** 특정 entity + revision 의 audit row (다중 필드 변경 시 N row). */
    List<UserAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);

    /** 다음 revision 채번 — 현재 entity 의 최대 revision_no 조회. 없으면 0. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(MAX(a.revisionNo), 0) FROM UserAuditLog a WHERE a.entityId = :entityId")
    int findMaxRevisionByEntityId(@org.springframework.data.repository.query.Param("entityId") UUID entityId);
}
