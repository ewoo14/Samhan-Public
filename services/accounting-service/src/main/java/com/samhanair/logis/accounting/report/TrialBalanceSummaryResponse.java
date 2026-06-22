package com.samhanair.logis.accounting.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 합계잔액시산표 응답.
 *
 * <p>기존 {@code /accounting/balances?period=yyyyMM} 시산표는 유지하고,
 * 본 응답은 임의기간과 일/월/기간 토글을 수용하는 보고서 전용 endpoint 에서 사용한다.
 */
public record TrialBalanceSummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        TrialBalanceGranularity granularity,
        List<TrialBalanceSummaryLine> rows,
        TrialBalanceSummaryTotals totals,
        LocalDateTime generatedAt
) {}
