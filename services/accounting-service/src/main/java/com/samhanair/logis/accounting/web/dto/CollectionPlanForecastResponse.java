package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 월별 수금 예상 집계 응답. */
public record CollectionPlanForecastResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalAmount,
        List<MonthlyBucket> months
) {
    /** YYYY-MM 월 버킷. */
    public record MonthlyBucket(String month, BigDecimal plannedAmount) {
    }
}
