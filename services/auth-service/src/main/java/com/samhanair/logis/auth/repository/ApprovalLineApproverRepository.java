package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.ApprovalLineApprover;
import com.samhanair.logis.auth.domain.ApproverType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 결재 역할별 다중 결재자 저장소. */
public interface ApprovalLineApproverRepository extends JpaRepository<ApprovalLineApprover, UUID> {

    List<ApprovalLineApprover> findByConfigRoleIdAndIsDeletedFalse(UUID configRoleId);

    boolean existsByConfigRoleIdAndApproverTypeAndApproverRefIdAndIsDeletedFalse(
            UUID configRoleId, ApproverType approverType, UUID approverRefId);

    /**
     * 특정 (유형, 참조 ID) 결재자가 활성 결재라인에 지정되어 있는지 — 권한그룹/계정 삭제 참조무결성 가드용.
     * A2-1c 가 결재자를 이 자식 테이블로 이관했으므로 레거시 approver_group_id 컬럼만 보는 가드는 불완전.
     */
    boolean existsByApproverTypeAndApproverRefIdAndIsDeletedFalse(
            ApproverType approverType, UUID approverRefId);

    Optional<ApprovalLineApprover> findByIdAndIsDeletedFalse(UUID id);
}
