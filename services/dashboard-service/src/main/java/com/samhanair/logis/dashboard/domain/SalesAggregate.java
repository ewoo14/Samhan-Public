package com.samhanair.logis.dashboard.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 매출 집계 entity — Phase 9 W4.
 *
 * <p>accounting-service (8087) + partner-order-service (8088) 데이터를 일별 / 거래처별로 집계한 row.
 * (aggregateDate, partnerId) 조합이 활성 행 기준 unique. amount 는 NUMERIC(20,4), itemCount 는 INT.
 *
 * <p>BaseEntity 7 audit + Soft Delete (`@SQLRestriction`).
 *
 * <p>UUID 비공개 가드 — partnerId 는 사용자 화면에 노출하지 않는다 (partner-service lookup 으로 partnerCode /
 * partnerName 매핑 후 노출).
 */
@Entity
@Getter
@Table(name = "sales_aggregates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SalesAggregate extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_date", nullable = false, updatable = false)
    private LocalDate aggregateDate;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private UUID partnerId;

    @Column(name = "amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    private SalesAggregate(LocalDate aggregateDate, UUID partnerId, BigDecimal amount, int itemCount) {
        if (aggregateDate == null) {
            throw new IllegalArgumentException("aggregateDate 필수");
        }
        if (partnerId == null) {
            throw new IllegalArgumentException("partnerId 필수");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount 필수");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount 음수 불가");
        }
        this.aggregateDate = aggregateDate;
        this.partnerId = partnerId;
        this.amount = amount;
        this.itemCount = itemCount;
    }

    /**
     * 신규 매출 집계 row.
     *
     * @param aggregateDate 집계 기준 일자
     * @param partnerId 거래처 UUID
     * @param amount 합계 금액 (NUMERIC(20,4))
     * @param itemCount 항목 수 (>= 0)
     * @return 영속화 가능한 신규 인스턴스
     */
    public static SalesAggregate of(LocalDate aggregateDate, UUID partnerId, BigDecimal amount, int itemCount) {
        return new SalesAggregate(aggregateDate, partnerId, amount, itemCount);
    }

    /** 집계 결과 갱신 (재집계 시점). */
    public void update(BigDecimal amount, int itemCount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount 필수");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount 음수 불가");
        }
        this.amount = amount;
        this.itemCount = itemCount;
    }
}
