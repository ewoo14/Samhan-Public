package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.TaxInvoiceLine;
import java.math.BigDecimal;
import java.util.UUID;

/** 세금계산서 라인 응답. UUID 는 mutation path 용 (FE 숨김 권장). */
public record TaxInvoiceLineResponse(
        UUID lineId,
        int lineNo,
        String itemName,
        String spec,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        String memo
) {
    public static TaxInvoiceLineResponse of(TaxInvoiceLine line) {
        return new TaxInvoiceLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getItemName(),
                line.getSpec(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getSupplyAmount(),
                line.getVatAmount(),
                line.getMemo()
        );
    }
}
