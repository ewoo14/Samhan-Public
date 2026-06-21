package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineConfigRepository extends JpaRepository<ApprovalLineConfig, UUID> {
    /** 전표 종류별 역할을 sequence 오름차순으로 조회(활성 행만 — @SQLRestriction). */
    List<ApprovalLineConfig> findByDocumentTypeOrderBySequenceAsc(String documentType);

    /** 전표 종류 + 액션 앵커로 활성 결재 역할을 조회한다. */
    Optional<ApprovalLineConfig> findByDocumentTypeAndActionKeyAndIsDeletedFalse(
            String documentType, String actionKey);

    /** 권한그룹이 활성 결재라인 설정에 지정되어 있는지 확인한다. */
    boolean existsByApproverGroupIdAndIsDeletedFalse(UUID approverGroupId);
}
