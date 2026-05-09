package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.AccountingPeriod;
import com.samhanair.logis.accounting.domain.PeriodStatus;
import com.samhanair.logis.accounting.domain.PeriodType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 회계 마감 기간 응답 — list / 단건. */
public record AccountingPeriodResponse(
        UUID id,
        PeriodType periodType,
        LocalDate periodDate,
        PeriodStatus status,
        LocalDateTime closedAt,
        String closedBy,
        LocalDateTime reversedAt,
        String reversedBy,
        BigDecimal totalSales,
        BigDecimal totalPurchase,
        BigDecimal totalExpense,
        int lockedSlipCount,
        String description
) {
    public static AccountingPeriodResponse of(AccountingPeriod p) {
        return new AccountingPeriodResponse(
                p.getId(),
                p.getPeriodType(),
                p.getPeriodDate(),
                p.getStatus(),
                p.getClosedAt(),
                p.getClosedBy(),
                p.getReversedAt(),
                p.getReversedBy(),
                p.getTotalSales(),
                p.getTotalPurchase(),
                p.getTotalExpense(),
                p.getLockedSlipCount(),
                p.getDescription()
        );
    }
}
