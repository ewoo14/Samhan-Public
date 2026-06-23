package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;

/**
 * 채권채무 현황 월별 aging 버킷.
 *
 * <p>분개 발생월 기준으로 기준일과 같은 월은 당월, 직전 1개월/2개월,
 * 3개월 이상 경과분을 3개월+ 로 분류한다.
 *
 * @param currentMonth    당월 발생 잔액
 * @param oneMonthElapsed 1개월 경과 잔액
 * @param twoMonthsElapsed 2개월 경과 잔액
 * @param threeMonthsOver 3개월 이상 경과 잔액
 */
public record ReceivablesPayablesAgingBuckets(
        BigDecimal currentMonth,
        BigDecimal oneMonthElapsed,
        BigDecimal twoMonthsElapsed,
        BigDecimal threeMonthsOver
) {
    public static ReceivablesPayablesAgingBuckets zero() {
        return new ReceivablesPayablesAgingBuckets(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public BigDecimal total() {
        return currentMonth
                .add(oneMonthElapsed)
                .add(twoMonthsElapsed)
                .add(threeMonthsOver);
    }
}
