package com.samhanair.logis.slip.attachment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 슬립 첨부 도메인 단위 테스트 — P1-8 (Stage 4) 시나리오 3종:
 * <ol>
 *   <li>upload (register factory + EXIF GPS / capturedAt 보존)</li>
 *   <li>public token 호환 (slipId 기반 register — token 검증은 controller 책임)</li>
 *   <li>Soft-delete (BaseEntity.markDeleted 위임)</li>
 * </ol>
 */
class SlipAttachmentTest {

    private static final UUID SLIP = UUID.randomUUID();

    @Test
    void register_upload_preservesAllMetadata() {
        BigDecimal lat = new BigDecimal("37.5172000");
        BigDecimal lng = new BigDecimal("127.0473000");
        LocalDateTime captured = LocalDateTime.of(2026, 5, 9, 14, 30);

        SlipAttachment a = SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "delivery_2026-05-09.jpg", 1024L, "image/jpeg",
                "slip-attachments/" + SLIP + "/abc.jpg",
                lat, lng, captured, "driver-1");

        assertThat(a.getSlipId()).isEqualTo(SLIP);
        assertThat(a.getAttachmentType()).isEqualTo(SlipAttachmentType.DELIVERY);
        assertThat(a.getFileName()).isEqualTo("delivery_2026-05-09.jpg");
        assertThat(a.getFileSize()).isEqualTo(1024L);
        assertThat(a.getContentType()).isEqualTo("image/jpeg");
        assertThat(a.getExifGpsLat()).isEqualByComparingTo("37.5172000");
        assertThat(a.getExifGpsLng()).isEqualByComparingTo("127.0473000");
        assertThat(a.getCapturedAt()).isEqualTo(captured);
        assertThat(a.getUploadedBy()).isEqualTo("driver-1");
        assertThat(a.getUploadedAt()).isNotNull();
    }

    @Test
    void register_publicToken_uploadedByDriver_attachmentTypeDelivery() {
        // public token 경로 시뮬레이션 — uploadedBy="driver", DELIVERY type 강제
        SlipAttachment a = SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "site.jpg", 2048L, "image/jpeg",
                "slip-attachments/" + SLIP + "/site.jpg",
                null, null, null, "driver");

        assertThat(a.getUploadedBy()).isEqualTo("driver");
        assertThat(a.getAttachmentType()).isEqualTo(SlipAttachmentType.DELIVERY);
        assertThat(a.getExifGpsLat()).isNull();
        assertThat(a.getExifGpsLng()).isNull();
        assertThat(a.getCapturedAt()).isNull();
    }

    @Test
    void softDelete_marksDeletedFlag() {
        SlipAttachment a = SlipAttachment.register(SLIP, SlipAttachmentType.INSPECTION,
                "inspect.png", 512L, "image/png",
                "slip-attachments/" + SLIP + "/inspect.png",
                null, null, null, "user-1");

        a.softDelete("user-2");

        assertThat(a.getIsDeleted()).isTrue();
        assertThat(a.getDeletedBy()).isEqualTo("user-2");
        assertThat(a.getDeletedAt()).isNotNull();
    }

    @Test
    void register_invalidParams_throws() {
        assertThatThrownBy(() -> SlipAttachment.register(null, SlipAttachmentType.DELIVERY,
                "f.jpg", 1L, "image/jpeg", "k", null, null, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SlipAttachment.register(SLIP, null,
                "f.jpg", 1L, "image/jpeg", "k", null, null, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "", 1L, "image/jpeg", "k", null, null, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "f.jpg", -1L, "image/jpeg", "k", null, null, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "f.jpg", 1L, "image/jpeg", "k", null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshStorageUrl_updatesUrl() {
        SlipAttachment a = SlipAttachment.register(SLIP, SlipAttachmentType.DELIVERY,
                "f.jpg", 1L, "image/jpeg", "k", null, null, null, "u");
        assertThat(a.getStorageUrl()).isNull();

        a.refreshStorageUrl("https://minio/presigned");
        assertThat(a.getStorageUrl()).isEqualTo("https://minio/presigned");
    }
}
