package com.samhanair.logis.partner.repository;

import com.samhanair.logis.partner.domain.AttachmentType;
import com.samhanair.logis.partner.domain.PartnerAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link PartnerAttachment} 저장소.
 *
 * <p>모든 query 는 {@code @SQLRestriction("is_deleted = false")} 가 자동 적용되어 활성 행만 반환.
 * Soft-deleted 행 조회가 필요한 감사/복구 케이스는 별도 Native Query 로 분리.
 */
@Repository
public interface PartnerAttachmentRepository extends JpaRepository<PartnerAttachment, UUID> {

    /** 거래처별 활성 첨부 전체 (업로드 역순 정렬은 service 계층에서 처리). */
    List<PartnerAttachment> findByPartnerIdAndIsDeletedFalse(UUID partnerId);

    /** 거래처 + 첨부 유형 조합 활성 첨부 (예: 사업자등록증만 조회). */
    List<PartnerAttachment> findByPartnerIdAndAttachmentTypeAndIsDeletedFalse(
            UUID partnerId, AttachmentType attachmentType);

    /**
     * MinIO storage key 중복 가드 — 동일 객체 key 재사용 방지 (idempotency).
     *
     * @param storageKey MinIO object key
     * @return 활성 첨부 중 동일 key 보유 시 true
     */
    boolean existsByStorageKey(String storageKey);
}
