package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자금 입출금내역 2기간 비교 보고서 응답.
 *
 * <p>공식 재무제표 현금흐름표와 별개로, 현금성 계정의 기간 입금/출금을
 * 상대계정별로 분해한다. 당기 기간과 직전 동일 일수 기간을 나란히 제공한다.
 *
 * @param current 당기 기간 자금 입출금내역
 * @param prior 직전 동일길이 기간 자금 입출금내역
 * @param generatedAt 보고서 생성 시각
 */
public record FundsFlowComparisonResponse(
        PeriodFlow current,
        PeriodFlow prior,
        LocalDateTime generatedAt
) {

    /**
     * 단일 기간 자금 입출금내역.
     *
     * @param fromDate 기간 시작일
     * @param toDate 기간 종료일
     * @param openingBalance 기초잔액
     * @param increases 증가 상대계정 라인
     * @param increaseSubtotal 증가 소계
     * @param decreases 감소 상대계정 라인
     * @param decreaseSubtotal 감소 소계
     * @param closingBalance 기말잔액
     * @param reconciled 기초+증가-감소=기말 검산 여부
     */
    public record PeriodFlow(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance,
            List<CounterAccountLine> increases,
            BigDecimal increaseSubtotal,
            List<CounterAccountLine> decreases,
            BigDecimal decreaseSubtotal,
            BigDecimal closingBalance,
            boolean reconciled
    ) {
    }

    /**
     * 상대계정별 금액 라인.
     *
     * @param counterAccountCode 상대계정 코드
     * @param counterAccountName 상대계정명
     * @param amount 금액
     */
    public record CounterAccountLine(
            String counterAccountCode,
            String counterAccountName,
            BigDecimal amount
    ) {
    }
}
