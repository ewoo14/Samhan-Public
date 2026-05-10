package com.samhanair.logis.user.editrequest.repository;

import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import com.samhanair.logis.user.editrequest.domain.UserEditRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user-service 수정/삭제 요청 repository — entityId / status / targetRole 기반 조회.
 * soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>핵심 lookup:
 * <ul>
 *   <li>{@link #findFirstByEntityIdAndStatus} — mutation 가드 (APPROVED 1건 lookup)</li>
 *   <li>{@link #findByTargetRoleAndStatusOrderByRequestedAtDesc} — 권한자 대시보드</li>
 *   <li>{@link #findByStatusAndExpiresAtBefore} — 스케줄러 자동 만료</li>
 * </ul>
 */
public interface UserEditRequestRepository extends JpaRepository<UserEditRequest, UUID> {

    /** entity 별 특정 status 의 첫 row (mutation 가드 — APPROVED 1건 lookup). */
    Optional<UserEditRequest> findFirstByEntityIdAndStatus(UUID entityId, EditRequestStatus status);

    /** 권한자 그룹 대시보드 — target_role + status (PENDING) 의 최신 요청 우선. */
    List<UserEditRequest> findByTargetRoleAndStatusOrderByRequestedAtDesc(EditTargetRole targetRole,
                                                                         EditRequestStatus status);

    /** 스케줄러 자동 만료 — PENDING + expires_at < now. */
    List<UserEditRequest> findByStatusAndExpiresAtBefore(EditRequestStatus status, LocalDateTime now);

    /** entity 별 전체 요청 이력 (요청 → 결정 timeline). */
    List<UserEditRequest> findByEntityIdOrderByRequestedAtDesc(UUID entityId);
}
