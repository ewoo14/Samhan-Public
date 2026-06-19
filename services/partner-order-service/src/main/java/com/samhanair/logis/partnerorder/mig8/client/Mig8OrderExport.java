package com.samhanair.logis.partnerorder.mig8.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** accounting-service MIG-8 주문 export wire-format mirror. */
public record Mig8OrderExport(
        String orderNo,
        UUID partnerId,
        String partnerName,
        String managerName,
        String progressStatus,
        LocalDate validUntil,
        String paymentTerms,
        String reference,
        BigDecimal totalSupplyAmount,
        BigDecimal totalVatAmount,
        String linkedSlipNo,
        String externalRef,
        List<Mig8OrderLineExport> lines) {
}
