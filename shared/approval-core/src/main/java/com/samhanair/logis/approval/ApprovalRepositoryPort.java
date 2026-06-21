package com.samhanair.logis.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * 결재선 영속성 SPI — 소비 서비스가 자기 repository 로 구현해 주입한다(collab-core Port 패턴).
 *
 * @param <L> 소비 서비스 concrete 결재선 타입
 */
public interface ApprovalRepositoryPort<L extends ApprovalLineBase> {

    Optional<L> findById(UUID approvalId);

    L save(L line);

    /** 전표 연계(loose ref) 조회 — (documentType, documentId) 로 결재선 1건. */
    Optional<L> findByDocument(String documentType, UUID documentId);
}
