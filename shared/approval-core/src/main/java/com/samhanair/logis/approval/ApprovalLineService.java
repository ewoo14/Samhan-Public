package com.samhanair.logis.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * 전 전표 공용 결재 엔진 서비스 — Port 위에서 승인/반려/회수/조회를 수행한다.
 *
 * <p>생성/목록/응답 매핑은 서비스마다 다르므로(채번·사용자검증·DTO) 본 제네릭에 넣지 않는다.
 * 소비 서비스는 자기 @Service 에서 본 클래스를 concrete 타입으로 인스턴스화해 재사용한다
 * (A2 slip-service 가 첫 실소비; groupware 는 자기 ApprovalLineService 유지).
 *
 * @param <L> concrete 결재선 타입
 */
public class ApprovalLineService<L extends ApprovalLineBase> {

    private final ApprovalRepositoryPort<L> repository;

    public ApprovalLineService(ApprovalRepositoryPort<L> repository) {
        this.repository = repository;
    }

    /** 단건 조회. 미존재 시 {@link IllegalArgumentException}(소비 서비스가 도메인 예외로 변환). */
    public L getOrThrow(UUID approvalId) {
        return repository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("결재선을 찾을 수 없습니다: " + approvalId));
    }

    /** 전표 연계(loose ref) 조회. */
    public Optional<L> findByDocument(String documentType, UUID documentId) {
        return repository.findByDocument(documentType, documentId);
    }

    /** 결재자 승인 — 현재 단계 결재자만 허용. */
    public L approve(UUID approvalId, UUID actorUserId) {
        L line = getOrThrow(approvalId);
        line.approve(actorUserId);
        return repository.save(line);
    }

    /** 결재자 반려. */
    public L reject(UUID approvalId, UUID actorUserId, String reason) {
        L line = getOrThrow(approvalId);
        line.reject(actorUserId, reason);
        return repository.save(line);
    }

    /** 요청자 회수. */
    public L withdraw(UUID approvalId, UUID actorUserId) {
        L line = getOrThrow(approvalId);
        line.withdraw(actorUserId);
        return repository.save(line);
    }
}
