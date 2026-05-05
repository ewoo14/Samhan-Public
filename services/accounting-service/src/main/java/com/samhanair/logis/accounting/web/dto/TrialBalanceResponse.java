package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 시산표 응답 — period (yyyyMM) + 7-그룹 행 + 합계.
 * 그룹 합계는 FE 가 rows 를 category 별로 sum 해 표시.
 */
public record TrialBalanceResponse(
        YearMonth period,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        List<TrialBalanceRowResponse> rows
) {}
