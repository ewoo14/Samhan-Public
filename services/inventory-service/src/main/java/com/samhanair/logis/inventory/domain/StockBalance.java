package com.samhanair.logis.inventory.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * (product, warehouse) 단위 재고 잔량 집계 + 낙관적 락. {@link Version} 으로 동시성 충돌 감지.
 * partial unique 인덱스: (product_id, warehouse_id) WHERE is_deleted = false (V1 SQL 참조).
 */
@Entity
@Getter
@Table(name = "stock_balances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class StockBalance extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private StockBalance(UUID productId, Warehouse warehouse) {
        this.productId = productId;
        this.warehouse = warehouse;
        this.availableQty = 0;
        this.reservedQty = 0;
        this.totalQty = 0;
        this.version = 0L;
    }

    /**
     * (productId, warehouse) 단위 신규 잔량 레코드를 생성한다. 모든 수량 0, version 0 으로 시작.
     *
     * @param productId 제품 UUID (product-service 의 logical reference)
     * @param warehouse 대상 창고 (영속 상태여야 함)
     * @return 영속화 전 StockBalance 인스턴스
     */
    public static StockBalance create(UUID productId, Warehouse warehouse) {
        return new StockBalance(productId, warehouse);
    }

    /**
     * 입고 — availableQty 와 totalQty 를 동시에 증가시킨다. reservedQty 는 변동 없음.
     *
     * @param amount 증가량 (1 이상)
     * @throws IllegalArgumentException amount 가 0 이하일 때
     */
    public void addInbound(int amount) {
        validatePositive(amount);
        this.availableQty += amount;
        this.totalQty += amount;
    }

    /**
     * 예약 — availableQty 에서 reservedQty 로 이동. totalQty 는 불변.
     *
     * @param amount 예약 수량 (1 이상)
     * @throws IllegalArgumentException amount 가 0 이하일 때
     * @throws IllegalStateException 가용 재고가 부족할 때 (서비스 레이어에서 CONFLICT 로 매핑)
     */
    public void reserve(int amount) {
        validatePositive(amount);
        if (amount > this.availableQty) {
            throw new IllegalStateException(
                    "가용 재고 부족: 요청 " + amount + ", 가용 " + this.availableQty);
        }
        this.availableQty -= amount;
        this.reservedQty += amount;
    }

    /**
     * 예약 해제 — reservedQty 에서 availableQty 로 되돌린다. totalQty 는 불변.
     *
     * @param amount 해제 수량 (1 이상)
     * @throws IllegalArgumentException amount 가 0 이하일 때
     * @throws IllegalStateException 예약 재고가 부족할 때 (서비스 레이어에서 CONFLICT 로 매핑)
     */
    public void release(int amount) {
        validatePositive(amount);
        if (amount > this.reservedQty) {
            throw new IllegalStateException(
                    "예약 재고 부족: 요청 " + amount + ", 예약 " + this.reservedQty);
        }
        this.reservedQty -= amount;
        this.availableQty += amount;
    }

    /**
     * 차감 — fromReservation=true 면 reservedQty 에서, false 면 availableQty 에서 빼고
     * totalQty 도 동시에 줄인다.
     *
     * @param amount 차감 수량 (1 이상)
     * @param fromReservation true 면 예약 재고 소진(출하 확정), false 면 가용 재고 직접 차감
     * @throws IllegalArgumentException amount 가 0 이하일 때
     * @throws IllegalStateException 해당 풀의 잔량이 부족할 때 (서비스 레이어에서 CONFLICT 로 매핑)
     */
    public void deduct(int amount, boolean fromReservation) {
        validatePositive(amount);
        if (fromReservation) {
            if (amount > this.reservedQty) {
                throw new IllegalStateException(
                        "예약 재고 부족: 요청 " + amount + ", 예약 " + this.reservedQty);
            }
            this.reservedQty -= amount;
        } else {
            if (amount > this.availableQty) {
                throw new IllegalStateException(
                        "가용 재고 부족: 요청 " + amount + ", 가용 " + this.availableQty);
            }
            this.availableQty -= amount;
        }
        this.totalQty -= amount;
    }

    /**
     * 조정 — delta 부호에 따라 availableQty 를 가감하고 totalQty 를 동기화. delta 0 이면 no-op.
     *
     * @param delta 부호 있는 변동량 (양수=가산, 음수=차감)
     * @throws IllegalStateException delta 차감 결과 availableQty 가 음수가 되는 경우
     *         (서비스 레이어에서 CONFLICT 로 매핑)
     */
    public void adjust(int delta) {
        if (delta == 0) {
            return;
        }
        int newAvailable = this.availableQty + delta;
        if (newAvailable < 0) {
            throw new IllegalStateException(
                    "조정 결과 가용 재고가 음수입니다: " + newAvailable);
        }
        this.availableQty = newAvailable;
        this.totalQty += delta;
    }

    private void validatePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다");
        }
    }
}
