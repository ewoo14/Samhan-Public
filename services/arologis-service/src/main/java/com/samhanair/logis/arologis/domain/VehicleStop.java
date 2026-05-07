package com.samhanair.logis.arologis.domain;

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
 * 정차 1건 = 카톡 라인 — Phase 10 W10-1.
 *
 * <p>(vehicleId, sequence) 가 활성 행 기준 unique. rawText 는 원본 카톡 라인 보존.
 * parsedAddress / parsedPartnerName / parsedPartnerCode / notes 는 KakaoDispatchParser 결과.
 *
 * <p>미해석 라인 ("상일상차" / "초월상차") 은 status=UNPARSED + rawText 만 보존.
 */
@Entity
@Getter
@Table(name = "vehicle_stops")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class VehicleStop extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "parsed_address", length = 500)
    private String parsedAddress;

    @Column(name = "parsed_partner_name", length = 200)
    private String parsedPartnerName;

    /** 전표번호 (사용자 노출 식별자, 카톡 "(에스엠하나공조-214)" 의 214). */
    @Column(name = "parsed_partner_code")
    private Long parsedPartnerCode;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StopStatus status;

    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    @Column(name = "actual_delivery_time")
    private LocalDateTime actualDeliveryTime;

    private VehicleStop(UUID vehicleId, Integer sequence, String rawText,
                        String parsedAddress, String parsedPartnerName, Long parsedPartnerCode,
                        String notes, StopStatus status) {
        if (vehicleId == null) {
            throw new IllegalArgumentException("vehicleId 필수");
        }
        if (sequence == null || sequence <= 0) {
            throw new IllegalArgumentException("sequence 는 1 이상");
        }
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText 필수");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 필수");
        }
        this.vehicleId = vehicleId;
        this.sequence = sequence;
        this.rawText = rawText;
        this.parsedAddress = parsedAddress;
        this.parsedPartnerName = parsedPartnerName;
        this.parsedPartnerCode = parsedPartnerCode;
        this.notes = notes;
        this.status = status;
    }

    /**
     * 신규 VehicleStop 생성.
     *
     * @param vehicleId 소속 vehicle UUID
     * @param sequence 차량 내 정차 순서 (1, 2, 3, ...)
     * @param rawText 카톡 원본 라인
     * @param parsedAddress 파싱된 주소 (옵션, 미해석 시 null)
     * @param parsedPartnerName 파싱된 사업자명 (옵션)
     * @param parsedPartnerCode 파싱된 전표번호 (옵션)
     * @param notes 특이사항 (옵션)
     * @param status 초기 상태 (PENDING 또는 UNPARSED)
     */
    public static VehicleStop of(UUID vehicleId, Integer sequence, String rawText,
                                 String parsedAddress, String parsedPartnerName, Long parsedPartnerCode,
                                 String notes, StopStatus status) {
        return new VehicleStop(vehicleId, sequence, rawText, parsedAddress, parsedPartnerName,
                parsedPartnerCode, notes, status);
    }

    /** 도착 (PENDING → ARRIVED). */
    public void markArrived(LocalDateTime now) {
        this.status = StopStatus.ARRIVED;
        this.actualArrivalTime = now;
    }

    /** 인수 완료 (ARRIVED → DELIVERED). */
    public void markDelivered(LocalDateTime now) {
        this.status = StopStatus.DELIVERED;
        this.actualDeliveryTime = now;
    }

    /** 실패 (PENDING / ARRIVED → FAILED). */
    public void markFailed(LocalDateTime now) {
        this.status = StopStatus.FAILED;
        this.actualDeliveryTime = now;
    }

    /** 상태 강제 갱신 (admin endpoint 용). */
    public void updateStatus(StopStatus next, LocalDateTime now) {
        switch (next) {
            case ARRIVED -> markArrived(now);
            case DELIVERED -> markDelivered(now);
            case FAILED -> markFailed(now);
            default -> this.status = next;
        }
    }
}
