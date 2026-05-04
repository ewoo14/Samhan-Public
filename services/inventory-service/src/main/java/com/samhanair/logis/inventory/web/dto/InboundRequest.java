package com.samhanair.logis.inventory.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** 입고 요청 — 새 StockLot 생성 + StockBalance 가산. */
public record InboundRequest(
        @NotNull UUID productId,
        @NotNull UUID warehouseId,
        @Size(max = 50) String lotNo,
        @NotNull @Positive Integer quantity,
        LocalDateTime receivedAt,
        @DecimalMin("0.00") BigDecimal unitCost,
        @Size(max = 500) String note) {
}
