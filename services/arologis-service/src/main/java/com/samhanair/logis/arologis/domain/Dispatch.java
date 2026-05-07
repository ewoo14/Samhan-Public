package com.samhanair.logis.arologis.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 배차 1건 = 카톡 1 메시지 — Phase 10 W10-1.
 *
 * <p>dispatchDate (8일착) + dispatchType (야상/주간) 두 필드가 핵심.
 * raw_kakao_text 는 원본 카톡 메시지 보존 (audit 용).
 *
 * <p>vehicles + stops 는 cascade 가 아니라 별도 entity (조회 시 JOIN FETCH 또는 service 단에서 로드).
 */
@Entity
@Getter
@Table(name = "dispatches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Dispatch extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_type", nullable = false, length = 20)
    private DispatchType dispatchType;

    @Column(name = "raw_kakao_text", columnDefinition = "TEXT")
    private String rawKakaoText;

    private Dispatch(LocalDate dispatchDate, DispatchType dispatchType, String rawKakaoText) {
        if (dispatchDate == null) {
            throw new IllegalArgumentException("dispatchDate 필수");
        }
        if (dispatchType == null) {
            throw new IllegalArgumentException("dispatchType 필수");
        }
        this.dispatchDate = dispatchDate;
        this.dispatchType = dispatchType;
        this.rawKakaoText = rawKakaoText;
    }

    /**
     * 신규 Dispatch 생성.
     *
     * @param dispatchDate 배차 도착 일자 (카톡 헤더 "8일착")
     * @param dispatchType 배차 유형 (DAY / NIGHT / EXPRESS)
     * @param rawKakaoText 카톡 원본 메시지 (audit 용)
     */
    public static Dispatch of(LocalDate dispatchDate, DispatchType dispatchType, String rawKakaoText) {
        return new Dispatch(dispatchDate, dispatchType, rawKakaoText);
    }
}
