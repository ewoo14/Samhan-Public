package com.samhanair.logis.dashboard.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * KPI 일/주/월 스냅샷 entity — Phase 9 W4.
 *
 * <p>category + snapshotDate 조합이 활성 행 기준 unique. value 는 NUMERIC(20,4) 로 저장하여
 * 금액 / 카운트 / 비율 모두 단일 컬럼으로 처리 가능.
 *
 * <p>BaseEntity 7 audit + Soft Delete (`@SQLRestriction`) 의무.
 *
 * <p>UUID 비공개 가드 — id (UUID) 는 외부 사용자 화면에 노출하지 않는다 (집계 카테고리명 / 날짜만 노출).
 */
@Entity
@Getter
@Table(name = "kpi_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class KpiSnapshot extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private KpiCategory category;

    @Column(name = "value", nullable = false, precision = 20, scale = 4)
    private BigDecimal value;

    private KpiSnapshot(LocalDate snapshotDate, KpiCategory category, BigDecimal value) {
        if (snapshotDate == null) {
            throw new IllegalArgumentException("snapshotDate 필수");
        }
        if (category == null) {
            throw new IllegalArgumentException("category 필수");
        }
        if (value == null) {
            throw new IllegalArgumentException("value 필수");
        }
        this.snapshotDate = snapshotDate;
        this.category = category;
        this.value = value;
    }

    /**
     * 신규 KPI 스냅샷 생성.
     *
     * @param snapshotDate 스냅샷 기준 일자
     * @param category KPI 카테고리
     * @param value 산출 값 (금액 / 카운트 / 비율 — NUMERIC(20,4))
     * @return 영속화 가능한 신규 인스턴스
     */
    public static KpiSnapshot of(LocalDate snapshotDate, KpiCategory category, BigDecimal value) {
        return new KpiSnapshot(snapshotDate, category, value);
    }

    /** 산출 값 갱신 (재집계 시점). */
    public void updateValue(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value 필수");
        }
        this.value = value;
    }
}
