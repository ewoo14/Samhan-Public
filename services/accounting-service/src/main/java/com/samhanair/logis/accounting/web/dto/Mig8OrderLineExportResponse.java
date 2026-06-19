package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** MIG-8 이관 주문 line 내부 export 응답. 사용자 화면 노출용 DTO가 아니다. */
public record Mig8OrderLineExportResponse(
        int lineNo,
        UUID productId,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        LocalDate itemDueDate
) {
}
