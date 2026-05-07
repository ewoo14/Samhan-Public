package com.samhanair.logis.dashboard.domain;

/**
 * 매출 집계 interval enum — Phase 9 W4.
 *
 * <ul>
 *   <li>{@link #DAILY} — 일별 집계 (default)</li>
 *   <li>{@link #WEEKLY} — 주별 집계 (월요일 기준 KST)</li>
 *   <li>{@link #MONTHLY} — 월별 집계</li>
 * </ul>
 *
 * <p>SalesAggregate 자체는 일별 row 기반. interval 값은 query 시점에 group-by 단위로 사용.
 */
public enum AggregateInterval {

    DAILY,
    WEEKLY,
    MONTHLY
}
