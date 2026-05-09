package com.samhanair.logis.partner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PartnerAttachment} 도메인 단위 테스트 — JPA / Spring 부팅 없음.
 *
 * <p>커버:
 * <ol>
 *   <li>register 정상 흐름 + 필수값 가드 (8개 필드)</li>
 *   <li>updateDescription / refreshStorageUrl 변경 반영</li>
 *   <li>softDelete → BaseEntity isDeleted=true / deletedBy 설정</li>
 * </ol>
 */
class PartnerAttachmentTest {

    private static final UUID PARTNER_ID = UUID.randomUUID();
    private static final UUID UPLOADER_ID = UUID.randomUUID();

    @Test
    void register_with_required_fields_initialises_uploaded_at() {
        PartnerAttachment a = PartnerAttachment.register(
                PARTNER_ID,
                AttachmentType.BIZ_LICENSE,
                "삼성에어컨_사업자등록증.png",
                123_456L,
                "image/png",
                "partner-attachments/abc/file.png",
                UPLOADER_ID,
                "본사 발급 사본");

        assertThat(a.getPartnerId()).isEqualTo(PARTNER_ID);
        assertThat(a.getAttachmentType()).isEqualTo(AttachmentType.BIZ_LICENSE);
        assertThat(a.getFileName()).isEqualTo("삼성에어컨_사업자등록증.png");
        assertThat(a.getFileSize()).isEqualTo(123_456L);
        assertThat(a.getMimeType()).isEqualTo("image/png");
        assertThat(a.getStorageKey()).isEqualTo("partner-attachments/abc/file.png");
        assertThat(a.getUploadedBy()).isEqualTo(UPLOADER_ID);
        assertThat(a.getDescription()).isEqualTo("본사 발급 사본");
        assertThat(a.getUploadedAt()).isNotNull();
        assertThat(a.getStorageUrl()).isNull();
    }

    @Test
    void register_rejects_null_partner_id() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                null, AttachmentType.BIZ_LICENSE, "a.png", 1L, "image/png",
                "k", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partnerId");
    }

    @Test
    void register_rejects_null_type() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, null, "a.png", 1L, "image/png",
                "k", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attachmentType");
    }

    @Test
    void register_rejects_blank_file_name() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, AttachmentType.BIZ_LICENSE, "  ", 1L, "image/png",
                "k", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileName");
    }

    @Test
    void register_rejects_negative_file_size() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, AttachmentType.BIZ_LICENSE, "a.png", -1L, "image/png",
                "k", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileSize");
    }

    @Test
    void register_rejects_blank_mime_type() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, AttachmentType.BIZ_LICENSE, "a.png", 1L, "  ",
                "k", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mimeType");
    }

    @Test
    void register_rejects_blank_storage_key() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, AttachmentType.BIZ_LICENSE, "a.png", 1L, "image/png",
                "  ", UPLOADER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storageKey");
    }

    @Test
    void register_rejects_null_uploader() {
        assertThatThrownBy(() -> PartnerAttachment.register(
                PARTNER_ID, AttachmentType.BIZ_LICENSE, "a.png", 1L, "image/png",
                "k", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uploadedBy");
    }

    @Test
    void update_description_changes_value() {
        PartnerAttachment a = sample();
        a.updateDescription("수정된 메모");
        assertThat(a.getDescription()).isEqualTo("수정된 메모");
    }

    @Test
    void update_description_allows_null() {
        PartnerAttachment a = sample();
        a.updateDescription(null);
        assertThat(a.getDescription()).isNull();
    }

    @Test
    void refresh_storage_url_caches_presigned() {
        PartnerAttachment a = sample();
        a.refreshStorageUrl("https://minio.local/presigned?token=abc");
        assertThat(a.getStorageUrl()).isEqualTo("https://minio.local/presigned?token=abc");
    }

    @Test
    void soft_delete_sets_audit_fields() {
        PartnerAttachment a = sample();
        a.softDelete("user-uuid-123");
        assertThat(a.getIsDeleted()).isTrue();
        assertThat(a.getDeletedBy()).isEqualTo("user-uuid-123");
        assertThat(a.getDeletedAt()).isNotNull();
    }

    private PartnerAttachment sample() {
        return PartnerAttachment.register(
                PARTNER_ID,
                AttachmentType.BUSINESS_CARD,
                "card.png",
                10_000L,
                "image/png",
                "partner-attachments/x/card.png",
                UPLOADER_ID,
                "원본 메모");
    }
}
