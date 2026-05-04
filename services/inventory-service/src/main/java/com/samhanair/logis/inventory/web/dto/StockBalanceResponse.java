package com.samhanair.logis.inventory.web.dto;

import com.samhanair.logis.inventory.domain.StockBalance;
import java.util.UUID;

/** (product, warehouse) 단위 재고 잔량 응답. */
public record StockBalanceResponse(
        UUID id,
        UUID productId,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        int availableQty,
        int reservedQty,
        int totalQty,
        Long version) {

    public static StockBalanceResponse from(StockBalance b) {
        return new StockBalanceResponse(
                b.getId(),
                b.getProductId(),
                b.getWarehouse().getId(),
                b.getWarehouse().getCode(),
                b.getWarehouse().getName(),
                b.getAvailableQty(),
                b.getReservedQty(),
                b.getTotalQty(),
                b.getVersion());
    }
}
