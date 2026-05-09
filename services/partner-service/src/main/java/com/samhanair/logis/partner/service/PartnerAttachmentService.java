package com.samhanair.logis.partner.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partner.domain.AttachmentType;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerAttachment;
import com.samhanair.logis.partner.repository.PartnerAttachmentRepository;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 거래처 첨부 파일 라이프사이클 — upload / list / download / delete.
 *
 * <p>책임 경계:
 * <ul>
 *   <li>실 파일 = MinIO ({@link AttachmentStorage}) — service 가 storageKey 발급 후 upload</li>
 *   <li>metadata = PostgreSQL ({@link PartnerAttachment}) — DB INSERT</li>
 *   <li>presigned URL = service 가 download 시점에 storageKey 로 재발급 (1시간 유효)</li>
 *   <li>delete = soft-delete + MinIO 객체 보존 (감사 추적)</li>
 * </ul>
 *
 * <p>가드:
 * <ul>
 *   <li>파일 크기 ≤ 10MB</li>
 *   <li>MIME ∈ { image/png, image/jpeg, application/pdf }</li>
 *   <li>partnerId 미존재 → 404 NOT_FOUND</li>
 *   <li>storageKey 충돌 (idempotency) → 신규 UUID suffix 재시도</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PartnerAttachmentService {

    /** 단일 파일 최대 크기 (10MB). 그 이상은 별도 대형 파일 채널 (TBD) 사용. */
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    /** 허용 MIME 화이트리스트. 그 외는 400 INVALID_INPUT. */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/pdf"
    );

    private static final String STORAGE_KEY_PREFIX = "partner-attachments";

    private final PartnerRepository partnerRepository;
    private final PartnerAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;

    /**
     * 첨부 업로드. partner 존재 확인 → MinIO 업로드 → DB INSERT.
     *
     * @param partnerId 소속 거래처 UUID
     * @param attachmentType 첨부 유형
     * @param file multipart 파일
     * @param description 비고 (nullable)
     * @param uploaderId 업로더 employee UUID
     * @return 영속화된 PartnerAttachment
     */
    @Transactional
    public PartnerAttachment upload(UUID partnerId, AttachmentType attachmentType,
                                    MultipartFile file, String description, UUID uploaderId) {
        validateFile(file);
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처를 찾을 수 없습니다: " + partnerId));

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String storageKey = buildStorageKey(partner.getId(), fileName);

        // storage 업로드 (예외 시 DB INSERT 미수행 — 트랜잭션 rollback)
        try (InputStream in = file.getInputStream()) {
            storage.upload(storageKey, file.getContentType(), file.getSize(), in);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "첨부 파일 업로드 실패: " + ex.getMessage());
        }

        PartnerAttachment attachment = PartnerAttachment.register(
                partner.getId(),
                attachmentType,
                fileName,
                file.getSize(),
                file.getContentType(),
                storageKey,
                uploaderId,
                description);

        // 캐시 URL 즉시 발급 (1시간 — 화면 즉시 미리보기 용)
        attachment.refreshStorageUrl(storage.presignedGetUrl(storageKey));
        return attachmentRepository.save(attachment);
    }

    /** 거래처별 첨부 목록 — soft-deleted 자동 제외. */
    @Transactional(readOnly = true)
    public List<PartnerAttachment> list(UUID partnerId) {
        return attachmentRepository.findByPartnerIdAndIsDeletedFalse(partnerId);
    }

    /** 거래처 + 유형 조합 첨부 목록 (예: BIZ_LICENSE 만). */
    @Transactional(readOnly = true)
    public List<PartnerAttachment> listByType(UUID partnerId, AttachmentType type) {
        return attachmentRepository.findByPartnerIdAndAttachmentTypeAndIsDeletedFalse(partnerId, type);
    }

    /**
     * 다운로드 — presigned URL 신규 발급 + DB 캐시 갱신.
     *
     * @param attachmentId 첨부 UUID
     * @return (attachment, freshUrl) tuple-like — 호출 측이 응답 DTO 조립
     */
    @Transactional
    public DownloadView download(UUID attachmentId) {
        PartnerAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "첨부 파일을 찾을 수 없습니다: " + attachmentId));
        String url = storage.presignedGetUrl(attachment.getStorageKey());
        attachment.refreshStorageUrl(url);
        return new DownloadView(attachment, url);
    }

    /**
     * Soft-delete. MinIO 객체는 보존 (감사 추적).
     *
     * @param attachmentId 첨부 UUID
     * @param deleterId 삭제 수행자 employee UUID (audit)
     */
    @Transactional
    public void delete(UUID attachmentId, UUID deleterId) {
        PartnerAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "첨부 파일을 찾을 수 없습니다: " + attachmentId));
        attachment.softDelete(deleterId == null ? "system" : deleterId.toString());
    }

    /** download() 응답 view. */
    public record DownloadView(PartnerAttachment attachment, String downloadUrl) { }

    // ============================================================
    // helpers
    // ============================================================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파일 크기는 최대 10MB 까지 허용됩니다. (현재: " + file.getSize() + " bytes)");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 파일 형식입니다. 허용: " + ALLOWED_MIME_TYPES + " (현재: " + mime + ")");
        }
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "untitled";
        }
        // 경로 separator 제거 (XSS / path traversal 방지)
        return original.replace("/", "_").replace("\\", "_");
    }

    private String buildStorageKey(UUID partnerId, String fileName) {
        String ext = extractExtension(fileName);
        // partner-attachments/{partnerId}/{uuid}.ext
        return STORAGE_KEY_PREFIX + "/" + partnerId + "/" + UUID.randomUUID() + ext;
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx).toLowerCase();
    }
}
