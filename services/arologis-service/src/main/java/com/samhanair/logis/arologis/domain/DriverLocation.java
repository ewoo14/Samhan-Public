package com.samhanair.logis.arologis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * GPS 추적 데이터 — Phase 10 W10-1.
 *
 * <p>BaseEntity 미상속 — 일별 partition + 30일 자동 cleanup 정책 (대용량 GPS 데이터 특성).
 * Soft Delete 도 미적용 — 30일 경과 시 hard DELETE.
 *
 * <p>capturedDate (DATE) 는 partition key 후보 — DriverLocationCleanupScheduler 의 30일 cleanup
 * 기준 컬럼. capturedAt (TIMESTAMPTZ) 는 정확한 시각.
 *
 * <p>NUMERIC(10,7) GPS — 약 1.1cm 정확도 (위도 1초 = 30m / 7th decimal = 1.1cm).
 */
@Entity
@Getter
@Table(name = "driver_locations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverLocation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** 30일 cleanup partition key. DATE 단위 (TIMESTAMPTZ 와 별개). */
    @Column(name = "captured_date", nullable = false)
    private LocalDate capturedDate;

    /** 보고 source (예: "DRIVER_APP" / "INSUNG_QUICK_CALLBACK"). */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    private DriverLocation(UUID driverId, BigDecimal latitude, BigDecimal longitude,
                           LocalDateTime capturedAt, String source) {
        if (driverId == null) {
            throw new IllegalArgumentException("driverId 필수");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("latitude / longitude 필수");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt 필수");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source 필수");
        }
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capturedAt = capturedAt;
        this.capturedDate = capturedAt.toLocalDate();
        this.source = source;
    }

    /**
     * 신규 GPS 위치 보고.
     *
     * @param driverId 기사 UUID
     * @param latitude 위도 (NUMERIC(10,7))
     * @param longitude 경도 (NUMERIC(10,7))
     * @param capturedAt 캡처 시각
     * @param source 보고 source ("DRIVER_APP" / "INSUNG_QUICK_CALLBACK" 등)
     */
    public static DriverLocation of(UUID driverId, BigDecimal latitude, BigDecimal longitude,
                                    LocalDateTime capturedAt, String source) {
        return new DriverLocation(driverId, latitude, longitude, capturedAt, source);
    }
}
