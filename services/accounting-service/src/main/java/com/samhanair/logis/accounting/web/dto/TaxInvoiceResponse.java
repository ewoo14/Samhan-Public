package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.TaxInvoice;
import com.samhanair.logis.accounting.domain.TaxInvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 세금계산서 헤더 응답 (페이지 조회용 — 라인 미포함). */
public record TaxInvoiceResponse(
        UUID id,
        String taxInvoiceNo,
        UUID partnerId,
        String partnerBusinessNo,
        String partnerName,
        LocalDate supplyDate,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        TaxInvoiceStatus status,
        LocalDateTime issuedAt,
        String issuedBy,
        UUID journalId,
        UUID reverseJournalId
) {
    public static TaxInvoiceResponse of(TaxInvoice ti) {
        return new TaxInvoiceResponse(
                ti.getId(),
                ti.getTaxInvoiceNo(),
                ti.getPartnerId(),
                ti.getPartnerBusinessNo(),
                ti.getPartnerName(),
                ti.getSupplyDate(),
                ti.getSupplyAmount(),
                ti.getVatAmount(),
                ti.getTotalAmount(),
                ti.getStatus(),
                ti.getIssuedAt(),
                ti.getIssuedBy(),
                ti.getJournalId(),
                ti.getReverseJournalId()
        );
    }
}
