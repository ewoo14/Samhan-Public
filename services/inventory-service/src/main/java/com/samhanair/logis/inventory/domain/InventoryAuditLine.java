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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 재고 실사 라인 — 제품별 시스템 재고(expected) vs 실물 재고(actual) 비교 단위.
 *
 * <p>snapshot 시점에 expected_qty / unit_cost / product_name 모두 고정 (이후 product/stock 변경
 * 영향을 받지 않음). actual_qty 는 실사자 입력 시 set, diff_qty / diff_amount 는 자동 계산.
 *
 * <p>UUID 비공개 원칙 (memory feedback_uuid_no_user_visibility) — productId 는 mutation key 로만,
 * 사용자 화면 표시는 product_name (snapshot) 사용.
 */
@Entity
@Getter
@Table(name = "inventory_audit_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class InventoryAuditLine extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private InventoryAudit audit;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "expected_qty", nullable = false)
    private int expectedQty;

    @Column(name = "actual_qty")
    private Integer actualQty;

    @Column(name = "diff_qty", nullable = false)
    private int diffQty;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "diff_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal diffAmount;

    @Column(name = "barcode_scanned", nullable = false)
    private boolean barcodeScanned;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    private InventoryAuditLine(InventoryAudit audit, UUID productId, String productName,
                               int expectedQty, BigDecimal unitCost) {
        if (audit == null || productId == null) {
            throw new IllegalArgumentException("audit / productId 는 필수입니다");
        }
        if (expectedQty < 0) {
            throw new IllegalArgumentException("expected_qty 는 0 이상이어야 합니다");
        }
        this.audit = audit;
        this.productId = productId;
        this.productName = productName == null ? "" : productName;
        this.expectedQty = expectedQty;
        this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        this.actualQty = null;
        this.diffQty = 0;
        this.diffAmount = BigDecimal.ZERO;
        this.barcodeScanned = false;
    }

    /**
     * 실사 시작 시점의 snapshot 라인을 생성한다. actual_qty 는 null 로 시작.
     *
     * @param audit 헤더 (영속 상태일 필요는 없음, cascade ALL)
     * @param productId 제품 UUID (logical reference)
     * @param productName snapshot 시점의 제품명 (이후 변경 불변)
     * @param expectedQty snapshot 시점의 system stock (총 재고 — available + reserved)
     * @param unitCost snapshot 시점의 단가 (lot 평균 또는 표준원가)
     * @return PLANNED 단계의 신규 InventoryAuditLine
     * @throws IllegalArgumentException audit/productId null 또는 expectedQty 음수일 때
     */
    public static InventoryAuditLine snapshot(InventoryAudit audit, UUID productId,
                                              String productName, int expectedQty,
                                              BigDecimal unitCost) {
        return new InventoryAuditLine(audit, productId, productName, expectedQty, unitCost);
    }

    /**
     * 실사자 입력 (또는 수정) — actual_qty 를 set 하고 diff_qty / diff_amount 자동 계산.
     * scanned=true 이면 barcode_scanned/scanned_at 도 갱신.
     *
     * @param actualQty 실사자가 측정한 실물 수량 (0 이상)
     * @param scanned true 면 바코드 스캔 입력 (모바일), false 면 수동 입력
     * @throws IllegalArgumentException actualQty 가 음수일 때
     */
    public void recordActual(int actualQty, boolean scanned) {
        if (actualQty < 0) {
            throw new IllegalArgumentException("actual_qty 는 0 이상이어야 합니다");
        }
        this.actualQty = actualQty;
        this.diffQty = actualQty - this.expectedQty;
        this.diffAmount = this.unitCost.multiply(BigDecimal.valueOf(this.diffQty));
        if (scanned) {
            this.barcodeScanned = true;
            this.scannedAt = LocalDateTime.now();
        }
    }
}
