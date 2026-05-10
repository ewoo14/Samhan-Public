package com.samhanair.logis.groupware.editrequest.repository;

import com.samhanair.logis.groupware.editrequest.domain.GroupwareEditRequest;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Groupware edit request repository — PR-H4b BE-E.
 *
 * <p>{@link org.hibernate.annotations.SQLRestriction} 가 entity-level 로 is_deleted=false 자동 적용.
 * APPROVED 소진 (consumeApproval) 도 markDeleted 패턴이라 자동 제외.
 */
public interface GroupwareEditRequestRepository extends JpaRepository<GroupwareEditRequest, UUID> {

    /** 도메인 entity 의 활성 (PENDING/APPROVED/REJECTED/EXPIRED) 요청 전체 (status 분기는 호출자). */
    List<GroupwareEditRequest> findByEntityIdOrderByRequestedAtDesc(UUID entityId);

    /** 도메인 entity 의 활성 APPROVED 요청 1건 (mutation 가드용 lookup). */
    Optional<GroupwareEditRequest> findFirstByEntityIdAndStatusOrderByRequestedAtDesc(
            UUID entityId, EditRequestStatus status);

    /** 권한자 그룹별 PENDING 목록 (대시보드 inbox). */
    List<GroupwareEditRequest> findByTargetRoleAndStatusOrderByRequestedAtDesc(
            EditTargetRole targetRole, EditRequestStatus status);

    /** 스케줄러 자동 만료 — PENDING + expires_at 도달 row. */
    List<GroupwareEditRequest> findByStatusAndExpiresAtBefore(
            EditRequestStatus status, LocalDateTime cutoff);
}
