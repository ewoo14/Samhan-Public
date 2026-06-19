package com.samhanair.logis.partnerorder.mig8.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** accounting-service MIG-8 주문 라인 export wire-format mirror. */
public record Mig8OrderLineExport(
        int lineNo,
        UUID productId,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        LocalDate itemDueDate) {
}
