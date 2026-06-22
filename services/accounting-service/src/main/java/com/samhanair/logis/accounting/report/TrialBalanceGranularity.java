package com.samhanair.logis.accounting.report;

/**
 * 합계잔액시산표 조회 단위.
 *
 * <p>DAY/MONTH 는 클라이언트 토글 상태를 명시하기 위한 값이며,
 * 실제 집계는 항상 {@code from/to} 임의기간으로 수행한다.
 */
public enum TrialBalanceGranularity {
    DAY,
    MONTH,
    RANGE
}
