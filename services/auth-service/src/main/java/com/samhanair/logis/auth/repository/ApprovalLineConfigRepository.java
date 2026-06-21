package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineConfigRepository extends JpaRepository<ApprovalLineConfig, UUID> {
    /** 전표 종류별 역할을 sequence 오름차순으로 조회(활성 행만 — @SQLRestriction). */
    List<ApprovalLineConfig> findByDocumentTypeOrderBySequenceAsc(String documentType);

    /**
     * 전표 종류 + 액션 앵커로 활성 결재 역할을 조회한다. action_key 는 (document_type) 당 1행이 정상이나,
     * 수동 DB 편집 등으로 다행이 되어도 sequence 오름차순 첫 행을 결정적으로 반환해 500(IncorrectResultSize)을 방어한다.
     */
    Optional<ApprovalLineConfig> findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(
            String documentType, String actionKey);

    /** 권한그룹이 활성 결재라인 설정에 지정되어 있는지 확인한다. */
    boolean existsByApproverGroupIdAndIsDeletedFalse(UUID approverGroupId);
}
