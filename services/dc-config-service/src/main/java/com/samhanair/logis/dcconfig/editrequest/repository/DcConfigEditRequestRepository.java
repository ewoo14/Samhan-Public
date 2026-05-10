package com.samhanair.logis.dcconfig.editrequest.repository;

import com.samhanair.logis.dcconfig.editrequest.domain.DcConfigEditRequest;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * dc-config-service 수정/삭제 요청 repository — PR-H4b.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 */
public interface DcConfigEditRequestRepository extends JpaRepository<DcConfigEditRequest, UUID> {

    /** entity 별 특정 status 의 첫 row (mutation 가드 — APPROVED 1건 lookup). */
    Optional<DcConfigEditRequest> findFirstByEntityIdAndStatus(UUID entityId, EditRequestStatus status);

    /** 권한자 그룹 대시보드 — target_role + status (PENDING) 의 최신 요청 우선. */
    List<DcConfigEditRequest> findByTargetRoleAndStatusOrderByRequestedAtDesc(EditTargetRole targetRole,
                                                                             EditRequestStatus status);

    /** 스케줄러 자동 만료 — PENDING + expires_at < now. */
    List<DcConfigEditRequest> findByStatusAndExpiresAtBefore(EditRequestStatus status, LocalDateTime now);

    /** entity 별 전체 요청 이력 (요청 → 결정 timeline). */
    List<DcConfigEditRequest> findByEntityIdOrderByRequestedAtDesc(UUID entityId);
}
