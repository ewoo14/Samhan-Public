package com.samhanair.logis.user.repository;

import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사원 서명 감사 이력 저장소 - C1a.
 * 단일 employeeId 별 이력 조회 + INSERT 만 지원(UPDATE/DELETE 금지 - 감사 무결성).
 */
public interface EmployeeSignatureAuditRepository
        extends JpaRepository<EmployeeSignatureAudit, UUID> {

    /** 사원별 전체 감사 이력 (created_at DESC). */
    List<EmployeeSignatureAudit> findAllByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
