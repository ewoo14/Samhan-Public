package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineConfigRepository extends JpaRepository<ApprovalLineConfig, UUID> {
    /** 전표 종류별 역할을 sequence 오름차순으로 조회(활성 행만 — @SQLRestriction). */
    List<ApprovalLineConfig> findByDocumentTypeOrderBySequenceAsc(String documentType);
}
