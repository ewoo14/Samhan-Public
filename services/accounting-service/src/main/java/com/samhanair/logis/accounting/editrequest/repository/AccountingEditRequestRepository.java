package com.samhanair.logis.accounting.editrequest.repository;

import com.samhanair.logis.accounting.editrequest.domain.AccountingEditRequest;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회계 도메인 수정/삭제 요청 — entityId / targetRole 기반 조회 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>핵심 사용처:
 * <ul>
 *   <li>{@link #findFirstByEntityIdAndStatus} — entity mutation 가드 (APPROVED 1건 있어야 진행)</li>
 *   <li>{@link #findByTargetRoleAndStatusOrderByRequestedAtDesc} — 권한자 대시보드</li>
 *   <li>{@link #findByEntityIdOrderByRequestedAtDesc} — entity 별 요청 이력</li>
 * </ul>
 */
public interface AccountingEditRequestRepository extends JpaRepository<AccountingEditRequest, UUID> {

    Optional<AccountingEditRequest> findFirstByEntityIdAndStatus(UUID entityId,
                                                                 EditRequestStatus status);

    List<AccountingEditRequest> findByEntityIdOrderByRequestedAtDesc(UUID entityId);

    List<AccountingEditRequest> findByTargetRoleAndStatusOrderByRequestedAtDesc(
            EditTargetRole targetRole, EditRequestStatus status);
}
