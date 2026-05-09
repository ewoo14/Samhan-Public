package com.samhanair.logis.slip.attachment.repository;

import com.samhanair.logis.slip.attachment.domain.SlipAttachment;
import com.samhanair.logis.slip.attachment.domain.SlipAttachmentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 슬립 첨부 파일 — slipId 또는 slipId+type 조합 조회. soft-delete 자동 제외. */
public interface SlipAttachmentRepository extends JpaRepository<SlipAttachment, UUID> {

    /** 슬립별 첨부 목록 — 업로드 순(uploadedAt asc). */
    List<SlipAttachment> findBySlipIdAndIsDeletedFalseOrderByUploadedAtAsc(UUID slipId);

    /** 슬립 + 유형 조합 첨부 목록. */
    List<SlipAttachment> findBySlipIdAndAttachmentTypeAndIsDeletedFalseOrderByUploadedAtAsc(
            UUID slipId, SlipAttachmentType attachmentType);
}
