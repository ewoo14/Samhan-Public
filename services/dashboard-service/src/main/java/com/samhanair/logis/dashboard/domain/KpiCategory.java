package com.samhanair.logis.dashboard.domain;

/**
 * KPI 카테고리 enum — Phase 9 W4.
 *
 * <ul>
 *   <li>{@link #DAILY_SALES} — 일별 매출 합계</li>
 *   <li>{@link #WEEKLY_SALES} — 주별 매출 합계</li>
 *   <li>{@link #MONTHLY_SALES} — 월별 매출 합계</li>
 *   <li>{@link #ORDER_COUNT} — 주문 건수</li>
 *   <li>{@link #ACTIVE_PARTNERS} — 활성 거래처 수</li>
 *   <li>{@link #STOCK_TURNOVER} — 재고 회전율</li>
 * </ul>
 *
 * <p>각 카테고리는 산출 공식이 다르며, KpiSnapshot 의 value 컬럼에 NUMERIC(20,4) 로 저장.
 * 산출 시점은 KpiSnapshot.snapshotDate (DATE) + createdAt (TIMESTAMP) 양쪽 기록.
 */
public enum KpiCategory {

    DAILY_SALES,
    WEEKLY_SALES,
    MONTHLY_SALES,
    ORDER_COUNT,
    ACTIVE_PARTNERS,
    STOCK_TURNOVER
}
