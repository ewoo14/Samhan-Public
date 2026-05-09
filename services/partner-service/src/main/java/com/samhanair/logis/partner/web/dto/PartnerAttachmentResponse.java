package com.samhanair.logis.partner.web.dto;

import com.samhanair.logis.partner.domain.AttachmentType;
import com.samhanair.logis.partner.domain.PartnerAttachment;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 거래처 첨부 파일 응답 DTO.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) 일관 — attachment id 와 partnerId
 * UUID 자체는 본 응답에 포함하지만 사용자 노출은 fileName / attachmentType / uploadedAt 등 식별 가능
 * 메타데이터로 한정. id 는 후속 download / delete 호출의 path variable 로만 사용.
 *
 * <p>{@link #downloadUrl} 는 service 계층이 발급한 presigned URL (1시간 유효) — 클라이언트는
 * 만료 전에 즉시 다운로드. 만료 후 다시 다운로드하려면 상세 조회 endpoint 재호출.
 *
 * @param id 첨부 UUID (path variable 용)
 * @param partnerId 소속 거래처 UUID
 * @param attachmentType 첨부 유형
 * @param fileName 원본 파일명 (사용자 노출)
 * @param fileSize 바이트 크기
 * @param mimeType MIME (image/png 등)
 * @param storageKey MinIO object key (운영 디버깅용)
 * @param downloadUrl presigned URL (1시간 유효, list 응답에서는 null)
 * @param description 비고
 * @param uploadedBy 업로더 employee UUID
 * @param uploadedAt 업로드 시각
 */
public record PartnerAttachmentResponse(
        UUID id,
        UUID partnerId,
        AttachmentType attachmentType,
        String fileName,
        Long fileSize,
        String mimeType,
        String storageKey,
        String downloadUrl,
        String description,
        UUID uploadedBy,
        LocalDateTime uploadedAt
) {

    /** presigned URL 없이 (목록 조회용) — downloadUrl=null. */
    public static PartnerAttachmentResponse from(PartnerAttachment a) {
        return from(a, null);
    }

    /** presigned URL 포함 (단건 조회 / 다운로드 endpoint). */
    public static PartnerAttachmentResponse from(PartnerAttachment a, String downloadUrl) {
        return new PartnerAttachmentResponse(
                a.getId(),
                a.getPartnerId(),
                a.getAttachmentType(),
                a.getFileName(),
                a.getFileSize(),
                a.getMimeType(),
                a.getStorageKey(),
                downloadUrl,
                a.getDescription(),
                a.getUploadedBy(),
                a.getUploadedAt());
    }
}
