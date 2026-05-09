package com.samhanair.logis.accounting.domain;

/**
 * 회계 마감 기간 유형 (P2-4 매출 마감).
 *
 * <ul>
 *   <li>{@link #DAILY} — 일별 마감. period_date 는 해당 일자.</li>
 *   <li>{@link #MONTHLY} — 월별 마감. period_date 는 해당 월의 1일 (조회 단순화).</li>
 * </ul>
 */
public enum PeriodType {
    /** 일별 매출 마감. */
    DAILY,

    /** 월별 매출 마감. */
    MONTHLY
}
