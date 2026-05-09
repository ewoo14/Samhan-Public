package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.TaxInvoice;
import com.samhanair.logis.accounting.domain.TaxInvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 세금계산서 단건 상세 — 라인 포함. */
public record TaxInvoiceDetailResponse(
        UUID id,
        String taxInvoiceNo,
        UUID partnerId,
        String partnerBusinessNo,
        String partnerName,
        String partnerAddress,
        LocalDate supplyDate,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        TaxInvoiceStatus status,
        LocalDateTime issuedAt,
        String issuedBy,
        LocalDateTime cancelledAt,
        String cancelledBy,
        UUID journalId,
        UUID reverseJournalId,
        String eTaxExternalId,
        String description,
        List<TaxInvoiceLineResponse> lines
) {
    public static TaxInvoiceDetailResponse of(TaxInvoice ti) {
        List<TaxInvoiceLineResponse> lineRes = ti.getLines().stream()
                .map(TaxInvoiceLineResponse::of)
                .toList();
        return new TaxInvoiceDetailResponse(
                ti.getId(),
                ti.getTaxInvoiceNo(),
                ti.getPartnerId(),
                ti.getPartnerBusinessNo(),
                ti.getPartnerName(),
                ti.getPartnerAddress(),
                ti.getSupplyDate(),
                ti.getSupplyAmount(),
                ti.getVatAmount(),
                ti.getTotalAmount(),
                ti.getStatus(),
                ti.getIssuedAt(),
                ti.getIssuedBy(),
                ti.getCancelledAt(),
                ti.getCancelledBy(),
                ti.getJournalId(),
                ti.getReverseJournalId(),
                ti.getETaxExternalId(),
                ti.getDescription(),
                lineRes
        );
    }
}
