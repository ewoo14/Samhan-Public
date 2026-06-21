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

    Optional<ApprovalLineApprover> findByIdAndIsDeletedFalse(UUID id);
}
