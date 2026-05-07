package com.samhanair.logis.dashboard.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 실시간 재고 캐시 entity — Phase 9 W4.
 *
 * <p>inventory-service (8085) 의 실시간 stock 데이터를 dashboard 가 캐시한 row.
 * (productId, warehouseCode) 조합이 활성 행 기준 unique. quantity 는 NUMERIC(20,4) — 분수 재고
 * (예: kg 단위 자재) 호환.
 *
 * <p>refreshedAt 은 inventory-service 호출 + cache 갱신 시점. BaseEntity 의 modifiedAt 과 별개로
 * "데이터 신선도" 의미를 명시 보유.
 *
 * <p>UUID 비공개 가드 — productId 는 사용자 화면에 노출하지 않는다 (productCode / warehouseCode 만 노출).
 */
@Entity
@Getter
@Table(name = "realtime_stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class RealTimeStock extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "warehouse_code", nullable = false, length = 20, updatable = false)
    private String warehouseCode;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 4)
    private BigDecimal quantity;

    @Column(name = "refreshed_at", nullable = false)
    private LocalDateTime refreshedAt;

    private RealTimeStock(UUID productId, String warehouseCode, BigDecimal quantity, LocalDateTime refreshedAt) {
        if (productId == null) {
            throw new IllegalArgumentException("productId 필수");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode 필수");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("quantity 필수");
        }
        this.productId = productId;
        this.warehouseCode = warehouseCode;
        this.quantity = quantity;
        this.refreshedAt = refreshedAt != null ? refreshedAt : LocalDateTime.now();
    }

    /**
     * 신규 실시간 재고 row.
     *
     * @param productId 제품 UUID (inventory-service 동기 키)
     * @param warehouseCode 창고 코드 (사용자 노출 식별자)
     * @param quantity 수량 (NUMERIC(20,4))
     * @param refreshedAt 갱신 시점 (null 시 now)
     * @return 영속화 가능한 신규 인스턴스
     */
    public static RealTimeStock of(UUID productId, String warehouseCode, BigDecimal quantity,
                                    LocalDateTime refreshedAt) {
        return new RealTimeStock(productId, warehouseCode, quantity, refreshedAt);
    }

    /**
     * 재고 수량 갱신 + refreshedAt 자동 now 적용.
     */
    public void refreshQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity 필수");
        }
        this.quantity = quantity;
        this.refreshedAt = LocalDateTime.now();
    }
}
