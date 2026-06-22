package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.domain.AccountCategory;
import java.math.BigDecimal;

/**
 * 합계잔액시산표 계정별 행.
 *
 * <p>eCount 합계잔액시산표 표준 4컬럼은
 * {@code debitBalance / debitTotal / creditTotal / creditBalance} 로 표현한다.
 * {@code openingBalance} 와 {@code closingBalance} 는 계정 성격별 정상 잔액 부호를 적용한 값이다.
 * 표의 차변/대변 잔액 컬럼은 {@code closingBalance} 의 부호와 정상 방향을 기준으로 한쪽에만
 * 절대값을 표시한다.
 */
public record TrialBalanceSummaryLine(
        String accountCode,
        String accountName,
        AccountCategory category,
        String categoryDisplayName,
        BigDecimal openingBalance,
        BigDecimal debitBalance,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal creditBalance,
        BigDecimal closingBalance
) {}
