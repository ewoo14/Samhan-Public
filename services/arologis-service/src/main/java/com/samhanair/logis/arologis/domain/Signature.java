package com.samhanair.logis.arologis.domain;

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
 * 전자서명 — Phase 10 W10-1.
 *
 * <p>slip-service 와의 통합은 W10-4 시점. 본 PR 은 entity + 저장 endpoint (driver-app POST) 만.
 *
 * <p>source = LINK (외부 기사 링크 서명) 또는 APP (본 어플 서명). APP 일 때 GPS 캡처 (capturedLatitude/Longitude).
 * imageRef 는 S3 또는 file-server 의 reference (실 저장은 W10-4 통합 시점).
 */
@Entity
@Getter
@Table(name = "signatures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Signature extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "stop_id", nullable = false)
    private UUID stopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SignatureSource source;

    @Column(name = "image_ref", length = 500)
    private String imageRef;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** GPS 위도 (NUMERIC(10,7) — 약 1cm 정확도). APP 일 때만 의미. */
    @Column(name = "captured_latitude", precision = 10, scale = 7)
    private BigDecimal capturedLatitude;

    @Column(name = "captured_longitude", precision = 10, scale = 7)
    private BigDecimal capturedLongitude;

    private Signature(UUID stopId, SignatureSource source, String imageRef,
                      LocalDateTime capturedAt, BigDecimal lat, BigDecimal lng) {
        if (stopId == null) {
            throw new IllegalArgumentException("stopId 필수");
        }
        if (source == null) {
            throw new IllegalArgumentException("source 필수");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt 필수");
        }
        this.stopId = stopId;
        this.source = source;
        this.imageRef = imageRef;
        this.capturedAt = capturedAt;
        this.capturedLatitude = lat;
        this.capturedLongitude = lng;
    }

    /**
     * 신규 Signature 생성.
     *
     * @param stopId 정차 UUID
     * @param source 서명 소스 (LINK / APP)
     * @param imageRef 이미지 reference (W10-4 통합 시점 file-server 경로)
     * @param capturedAt 서명 시각
     * @param lat GPS 위도 (APP 일 때)
     * @param lng GPS 경도 (APP 일 때)
     */
    public static Signature of(UUID stopId, SignatureSource source, String imageRef,
                               LocalDateTime capturedAt, BigDecimal lat, BigDecimal lng) {
        return new Signature(stopId, source, imageRef, capturedAt, lat, lng);
    }
}
