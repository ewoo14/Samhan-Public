package com.samhanair.logis.groupware.repository;

import com.samhanair.logis.groupware.domain.ApprovalLine;
import com.samhanair.logis.groupware.domain.ApprovalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 결재선 저장소 — 요청자 / 결재자 / 상태 검색. */
@Repository
public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, UUID> {

    /** 요청자별 결재선 페이지 — 본인 결재선 inbox 조회. */
    Page<ApprovalLine> findAllByRequesterId(UUID requesterId, Pageable pageable);

    /** 상태별 페이지 — 관리자/감사용. */
    Page<ApprovalLine> findAllByStatus(ApprovalStatus status, Pageable pageable);

    /** 요청자 + 상태 필터 — 본인 미결 결재선 등. */
    List<ApprovalLine> findAllByRequesterIdAndStatus(UUID requesterId, ApprovalStatus status);
}
