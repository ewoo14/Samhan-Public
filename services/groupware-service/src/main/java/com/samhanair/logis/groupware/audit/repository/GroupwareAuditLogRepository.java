package com.samhanair.logis.groupware.audit.repository;

import com.samhanair.logis.groupware.audit.domain.GroupwareAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Groupware audit log repository — PR-H4b BE-E.
 *
 * <p>{@link org.hibernate.annotations.SQLRestriction} 가 entity-level 로 is_deleted=false 자동 적용.
 */
public interface GroupwareAuditLogRepository extends JpaRepository<GroupwareAuditLog, UUID> {

    /**
     * 특정 도메인 entity 의 audit timeline 조회 (revision_no DESC + changed_at DESC).
     *
     * @param entityId 도메인 entity UUID (ApprovalLine.id / Message.id / Schedule.id)
     * @return 최신 변경 우선 정렬 audit row 목록
     */
    List<GroupwareAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    /**
     * 특정 도메인 entity 의 다음 revision_no 채번 — 현재 max + 1 (없으면 1).
     */
    default int nextRevisionNo(UUID entityId) {
        return findByEntityIdOrderByRevisionNoDescChangedAtDesc(entityId).stream()
                .mapToInt(GroupwareAuditLog::getRevisionNo)
                .max()
                .orElse(0) + 1;
    }
}
