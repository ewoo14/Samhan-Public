package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SlipLine;
import java.math.BigDecimal;
import java.util.UUID;

/** 라인 응답 — id, product 정보, 수량, 단가, lineTotal, note. */
public record SlipLineResponse(
        UUID id,
        UUID productId,
        String productName,
        String modelName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String note) {

    public static SlipLineResponse from(SlipLine line) {
        return new SlipLineResponse(
                line.getId(),
                line.getProductId(),
                line.getProductName(),
                line.getModelName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineTotal(),
                line.getNote());
    }
}
