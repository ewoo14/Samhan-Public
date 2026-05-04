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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/** 이동전표 라인 — productId + 요청/출고/입고 수량 + 양쪽 lot 참조. */
@Entity
@Getter
@Table(name = "stock_transfer_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class StockTransferLine extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(name = "shipped_quantity", nullable = false)
    private int shippedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "source_lot_id")
    private UUID sourceLotId;

    @Column(name = "destination_lot_id")
    private UUID destinationLotId;

    private StockTransferLine(StockTransfer transfer, UUID productId, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("요청 수량은 0보다 커야 합니다");
        }
        this.transfer = transfer;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.shippedQuantity = 0;
        this.receivedQuantity = 0;
    }

    /**
     * 이동전표 라인 1건을 생성한다. 출고/입고 수량은 0 으로 시작.
     *
     * @param transfer 헤더 (영속 상태일 필요는 없음, cascade ALL)
     * @param productId 제품 UUID (서비스 레이어에서 ProductClient 로 사전 검증)
     * @param requestedQuantity 요청 수량 (1 이상)
     * @return 영속화 전 StockTransferLine 인스턴스
     * @throws IllegalArgumentException requestedQuantity 가 0 이하일 때
     */
    public static StockTransferLine create(StockTransfer transfer, UUID productId, int requestedQuantity) {
        return new StockTransferLine(transfer, productId, requestedQuantity);
    }

    /**
     * 출하 기록 — ship() 시 source lot 차감 결과를 기록.
     *
     * @param quantity 실제 출하 수량
     * @param sourceLotId FIFO 차감된 source lot ID
     */
    public void recordShipment(int quantity, UUID sourceLotId) {
        this.shippedQuantity = quantity;
        this.sourceLotId = sourceLotId;
    }

    /**
     * 입고 기록 — receive() 시 destination 신규 lot 생성 결과를 기록.
     *
     * @param quantity 실제 입고 수량
     * @param destinationLotId destination 창고에 새로 생성된 lot ID
     */
    public void recordReceipt(int quantity, UUID destinationLotId) {
        this.receivedQuantity = quantity;
        this.destinationLotId = destinationLotId;
    }
}
