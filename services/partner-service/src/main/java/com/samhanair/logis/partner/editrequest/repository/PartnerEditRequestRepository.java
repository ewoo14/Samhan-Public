package com.samhanair.logis.partner.editrequest.repository;

import com.samhanair.logis.partner.editrequest.domain.PartnerEditRequest;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래처 도메인 수정/삭제 요청 — entityId / targetRole 기반 조회 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>soft-delete 자동 제외 ({@code @SQLRestriction}).
 */
public interface PartnerEditRequestRepository extends JpaRepository<PartnerEditRequest, UUID> {

    Optional<PartnerEditRequest> findFirstByEntityIdAndStatus(UUID entityId,
                                                              EditRequestStatus status);

    List<PartnerEditRequest> findByEntityIdOrderByRequestedAtDesc(UUID entityId);

    List<PartnerEditRequest> findByTargetRoleAndStatusOrderByRequestedAtDesc(
            EditTargetRole targetRole, EditRequestStatus status);
}
