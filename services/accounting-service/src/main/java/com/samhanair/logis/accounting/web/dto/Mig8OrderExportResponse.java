package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * MIG-8 이관 주문 내부 export 응답.
 *
 * <p>{@code partnerId}는 partner-order-service 이식 작업을 위한 service-to-service 식별자이며,
 * 사용자 화면에 노출하는 public/admin endpoint 용 DTO가 아니다.
 */
public record Mig8OrderExportResponse(
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
        List<Mig8OrderLineExportResponse> lines
) {
}
