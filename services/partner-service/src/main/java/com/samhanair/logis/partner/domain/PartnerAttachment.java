package com.samhanair.logis.partner.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처 첨부 파일 (사업자등록증 / 명함 / 세금계산서 / 계약서 등).
 *
 * <p>{@link Partner} 와 1:N (partner 1건당 N 첨부). 본 entity 는 별도 table
 * ({@code partner_attachments}) 로 보관 — Partner entity 의 27 필드 보강 작업과 충돌 회피.
 *
 * <p><b>저장 전략</b>: 실 파일은 MinIO (S3 호환) 에 저장하고 본 row 는 metadata 만 보유.
 * {@link #storageKey} = MinIO object key, {@link #storageUrl} = presigned URL (1시간 유효 권장).
 * 실 파일 다운로드 시 service 계층이 storageKey 로 presigned URL 을 재발급한다 — DB 의
 * storageUrl 은 직전 발급 캐시 (만료 가능).
 *
 * <p><b>Soft-delete</b>: {@code @SQLRestriction("is_deleted = false")} + BaseEntity 의
 * {@link BaseEntity#markDeleted(String)} 사용. 회계 감사 추적 목적으로 MinIO 객체 자체는 보존하고
 * DB row 만 soft-delete.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) — 본 entity 의 id 는 path
 * variable 로만 사용, 사용자 화면 노출은 fileName 으로 한다.
 */
@Entity
@Getter
@Table(name = "partner_attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerAttachment extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 소속 거래처 FK ({@link Partner#getId()}). */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /** 첨부 유형. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 30)
    private AttachmentType attachmentType;

    /** 원본 파일명 (예: "삼성에어컨_사업자등록증.png"). */
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** 바이트 크기. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME (image/png, image/jpeg, application/pdf 등). */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** MinIO object key (예: "partner-attachments/{partnerId}/{uuid}.png"). */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** presigned URL 캐시 (만료 가능 — 실 다운로드 시 재발급 권장). */
    @Column(name = "storage_url", length = 1000)
    private String storageUrl;

    /** 비고 (사용자 메모). */
    @Column(name = "description", length = 500)
    private String description;

    /** 업로드 한 사용자 (employee UUID). */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /** 업로드 시각 (audit createdAt 와 별도 — 사용자 화면 노출용). */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    private PartnerAttachment(UUID partnerId, AttachmentType attachmentType, String fileName,
                              Long fileSize, String mimeType, String storageKey,
                              UUID uploadedBy, String description) {
        if (partnerId == null) {
            throw new IllegalArgumentException("partnerId 필수");
        }
        if (attachmentType == null) {
            throw new IllegalArgumentException("attachmentType 필수");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName 필수");
        }
        if (fileSize == null || fileSize < 0L) {
            throw new IllegalArgumentException("fileSize 는 0 이상 필수");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType 필수");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey 필수");
        }
        if (uploadedBy == null) {
            throw new IllegalArgumentException("uploadedBy 필수");
        }
        this.partnerId = partnerId;
        this.attachmentType = attachmentType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.description = description;
        this.uploadedAt = LocalDateTime.now();
    }

    /**
     * 신규 첨부 등록 정적 factory.
     *
     * @param partnerId 소속 거래처 UUID
     * @param attachmentType 첨부 유형
     * @param fileName 원본 파일명
     * @param fileSize 바이트 크기
     * @param mimeType MIME 문자열
     * @param storageKey MinIO object key
     * @param uploadedBy 업로더 employee UUID
     * @param description 비고 (nullable)
     * @return 영속화 전 신규 PartnerAttachment
     */
    public static PartnerAttachment register(UUID partnerId, AttachmentType attachmentType,
                                             String fileName, Long fileSize, String mimeType,
                                             String storageKey, UUID uploadedBy,
                                             String description) {
        return new PartnerAttachment(partnerId, attachmentType, fileName, fileSize, mimeType,
                storageKey, uploadedBy, description);
    }

    /**
     * presigned URL 캐시 갱신 (service 계층에서 재발급 후 호출). 영속화는 dirty checking.
     */
    public void refreshStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    /**
     * 비고 수정 (파일 메타 자체는 immutable — replace 시 신규 row + 기존 soft-delete 권장).
     *
     * @param newDescription 새 비고 (null/blank 도 허용 — 메모 제거 의미)
     */
    public void updateDescription(String newDescription) {
        this.description = newDescription;
    }

    /**
     * Soft-delete. BaseEntity 의 {@link BaseEntity#markDeleted(String)} 위임.
     * MinIO 객체 자체는 감사 추적을 위해 유지 (별도 retention job 처리).
     *
     * @param deleterUserId 삭제 수행자 (audit deletedBy)
     */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId);
    }
}
