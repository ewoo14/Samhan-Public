package com.samhanair.logis.inventory.domain;

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
import org.hibernate.annotations.UuidGenerator;

/**
 * 재고 이동 감사 로그 — append-only. soft-delete 미사용 (is_deleted 컬럼은 BaseEntity 호환을 위해
 * 존재하지만 항상 false). {@link #quantityDelta} 는 부호 있는 정수 (입고 +, 차감 -).
 */
@Entity
@Getter
@Table(name = "stock_movements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_user_id", nullable = false, length = 50)
    private String actorUserId;

    private StockMovement(UUID lotId, UUID productId, UUID warehouseId,
                          MovementType movementType, int quantityDelta,
                          String referenceType, UUID referenceId,
                          String note, LocalDateTime occurredAt, String actorUserId) {
        this.lotId = lotId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.note = note;
        this.occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        this.actorUserId = actorUserId;
    }

    /**
     * 재고 변동 이벤트 1건을 기록한다 — append-only. occurredAt 은 항상 now() 로 고정.
     *
     * @param lotId 변동 대상 lot ID (조정 movement 의 경우 balance.id 로 대체)
     * @param productId 제품 UUID
     * @param warehouseId 창고 UUID
     * @param movementType 이벤트 종류 (INBOUND/RESERVE/RELEASE/DEDUCT/ADJUST/TRANSFER_OUT/TRANSFER_IN)
     * @param quantityDelta 부호 있는 수량 변화 (입고/가산 +, 차감/RESERVE -)
     * @param referenceType 외부 참조 종류 (예: "ORDER", "TRANSFER", "ADJUST" 등; 선택)
     * @param referenceId 외부 참조 ID (선택)
     * @param note 자유 메모 (최대 500자, 선택)
     * @param actorUserId 행위자 user-id (gateway X-User-Id 또는 "system")
     * @return 영속화 전 StockMovement 인스턴스
     */
    public static StockMovement of(UUID lotId, UUID productId, UUID warehouseId,
                                   MovementType movementType, int quantityDelta,
                                   String referenceType, UUID referenceId,
                                   String note, String actorUserId) {
        return new StockMovement(lotId, productId, warehouseId, movementType, quantityDelta,
                referenceType, referenceId, note, LocalDateTime.now(), actorUserId);
    }
}
