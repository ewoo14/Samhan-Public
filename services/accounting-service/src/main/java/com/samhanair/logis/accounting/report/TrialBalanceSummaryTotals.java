package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;

/**
 * 합계잔액시산표 총계.
 *
 * @param openingBalanceTotal 이월잔액 총계(계정 정상 잔액 부호 기준)
 * @param debitBalanceTotal   차변 잔액 컬럼 합계
 * @param debitTotal          기간 차변 합계
 * @param creditTotal         기간 대변 합계
 * @param creditBalanceTotal  대변 잔액 컬럼 합계
 * @param closingBalanceTotal 기말잔액 총계(계정 정상 잔액 부호 기준)
 * @param balanced            차변 잔액 컬럼 합계와 대변 잔액 컬럼 합계 일치 여부
 */
public record TrialBalanceSummaryTotals(
        BigDecimal openingBalanceTotal,
        BigDecimal debitBalanceTotal,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal creditBalanceTotal,
        BigDecimal closingBalanceTotal,
        boolean balanced
) {}
