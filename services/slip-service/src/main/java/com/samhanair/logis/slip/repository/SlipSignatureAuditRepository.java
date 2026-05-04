package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.SlipSignatureAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 전자서명 감사 이력 — Slice C (signature-slice-C Plan §3.1).
 * 단일 slipId 별 이력 조회 + 전체 INSERT 만 지원 (UPDATE/DELETE 금지 — 감사 무결성).
 */
public interface SlipSignatureAuditRepository extends JpaRepository<SlipSignatureAudit, UUID> {

    /** 특정 슬립의 전체 감사 이력 (created_at DESC). admin 조회 화면 source. */
    List<SlipSignatureAudit> findAllBySlipIdOrderByCreatedAtDesc(UUID slipId);
}
