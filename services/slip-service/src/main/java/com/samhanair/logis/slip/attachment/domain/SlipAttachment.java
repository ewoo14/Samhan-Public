package com.samhanair.logis.slip.attachment.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 슬립 첨부 파일 — P1-8 (Stage 4) 모바일 사진 첨부.
 *
 * <p>{@link com.samhanair.logis.slip.domain.Slip} 와 1:N (slip 1건당 N 첨부). 별도 table
 * ({@code slip_attachments}) 로 보관 — Slip entity 의 30+ 필드 보강과 충돌 회피.
 *
 * <p><b>저장 전략</b>: 실 파일은 MinIO (S3 호환) bucket {@code slip-attachments} 에 저장하고
 * 본 row 는 metadata + EXIF GPS 만 보유. {@link #storageKey} = MinIO object key,
 * {@link #storageUrl} = presigned URL (1시간 유효 권장).
 *
 * <p>partner-service 의 {@code PartnerAttachment} 와 동일 패턴 + EXIF GPS / capturedAt 추가.
 *
 * <p><b>Soft-delete</b>: {@code @SQLRestriction("is_deleted = false")} + BaseEntity.markDeleted.
 * 회계 감사 추적 목적으로 MinIO 객체 자체는 보존.
 */
@Entity
@Getter
@Table(name = "slip_attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipAttachment extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 소속 Slip FK ({@link com.samhanair.logis.slip.domain.Slip#getId()}). */
    @Column(name = "slip_id", nullable = false)
    private UUID slipId;

    /** 첨부 유형 — DELIVERY / INSPECTION / ESTIMATE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 20)
    private SlipAttachmentType attachmentType;

    /** 원본 파일명. */
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** 바이트 크기. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME (image/jpeg / image/png / application/pdf). */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** MinIO object key (예: "slip-attachments/{slipId}/{uuid}.jpg"). */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** presigned URL 캐시 (만료 가능 — 실 다운로드 시 재발급 권장). */
    @Column(name = "storage_url", length = 1000)
    private String storageUrl;

    /** EXIF GPS 위도 (선택, 모바일 카메라 촬영 시 자동 추출). */
    @Column(name = "exif_gps_lat", precision = 10, scale = 7)
    private BigDecimal exifGpsLat;

    /** EXIF GPS 경도 (선택). */
    @Column(name = "exif_gps_lng", precision = 10, scale = 7)
    private BigDecimal exifGpsLng;

    /** 실 촬영 시각 (선택, EXIF DateTime). */
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    /** 업로더 user-id (gateway X-User-Id 또는 mobile-staff token sub). */
    @Column(name = "uploaded_by", nullable = false, length = 50)
    private String uploadedBy;

    /** 업로드 시각 — audit createdAt 와 별도 (사용자 화면 노출용). */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    private SlipAttachment(UUID slipId, SlipAttachmentType attachmentType, String fileName,
                           Long fileSize, String contentType, String storageKey,
                           BigDecimal exifGpsLat, BigDecimal exifGpsLng, LocalDateTime capturedAt,
                           String uploadedBy) {
        if (slipId == null) {
            throw new IllegalArgumentException("slipId 는 필수입니다");
        }
        if (attachmentType == null) {
            throw new IllegalArgumentException("attachmentType 은 필수입니다");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName 은 필수입니다");
        }
        if (fileSize == null || fileSize < 0L) {
            throw new IllegalArgumentException("fileSize 는 0 이상이어야 합니다");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType 은 필수입니다");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey 는 필수입니다");
        }
        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException("uploadedBy 는 필수입니다");
        }
        this.slipId = slipId;
        this.attachmentType = attachmentType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.exifGpsLat = exifGpsLat;
        this.exifGpsLng = exifGpsLng;
        this.capturedAt = capturedAt;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }

    /**
     * 신규 첨부 등록 정적 factory.
     *
     * @param slipId 소속 Slip UUID
     * @param attachmentType 첨부 유형
     * @param fileName 원본 파일명
     * @param fileSize 바이트 크기
     * @param contentType MIME
     * @param storageKey MinIO object key
     * @param exifGpsLat EXIF GPS 위도 (선택)
     * @param exifGpsLng EXIF GPS 경도 (선택)
     * @param capturedAt EXIF 촬영 시각 (선택)
     * @param uploadedBy 업로더 user-id
     * @return 영속화 전 신규 SlipAttachment
     */
    public static SlipAttachment register(UUID slipId, SlipAttachmentType attachmentType,
                                          String fileName, Long fileSize, String contentType,
                                          String storageKey,
                                          BigDecimal exifGpsLat, BigDecimal exifGpsLng,
                                          LocalDateTime capturedAt, String uploadedBy) {
        return new SlipAttachment(slipId, attachmentType, fileName, fileSize, contentType,
                storageKey, exifGpsLat, exifGpsLng, capturedAt, uploadedBy);
    }

    /** presigned URL 캐시 갱신. */
    public void refreshStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    /**
     * Soft-delete. BaseEntity.markDeleted 위임. MinIO 객체는 감사 추적 위해 보존.
     */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId);
    }
}
