package com.samhanair.logis.accounting.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 월별손익분석 응답 DTO.
 *
 * <p>당기 회계연도 12개월을 계정 행 × 월 컬럼 매트릭스로 제공하고, 각 행마다 전기
 * 연간 합계를 비교 컬럼으로 제공한다.
 *
 * @param fiscalYear 당기 회계연도
 * @param priorYear  전기 회계연도
 * @param fromDate   당기 시작일
 * @param toDate     당기 종료일
 * @param months     월 컬럼 목록 (1~12)
 * @param rows       계정/소계/합계 행 목록
 * @param generatedAt 보고서 생성 시각
 */
public record MonthlyIncomeStatementResponse(
        int fiscalYear,
        int priorYear,
        LocalDate fromDate,
        LocalDate toDate,
        List<Integer> months,
        List<MonthlyIncomeStatementLine> rows,
        LocalDateTime generatedAt
) {
}
