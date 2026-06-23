package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * 월별손익분석 매트릭스의 단일 행.
 *
 * <p>행은 계정 행과 소계/합계 행을 함께 표현한다. 계정 행은 {@code rowKind=ACCOUNT},
 * 매출총이익/영업이익 등 산식 행은 {@code rowKind=SUBTOTAL} 또는 {@code TOTAL} 로 내려보낸다.
 *
 * @param rowKind        행 유형 (ACCOUNT / SUBTOTAL / TOTAL)
 * @param section        손익 섹션 코드
 * @param accountCode    계정 코드. 산식 행이면 null
 * @param accountName    표시 계정명 또는 산식명
 * @param category       계정 카테고리 문자열. 산식 행이면 null
 * @param monthlyAmounts 당기 1월~12월 금액 배열
 * @param annualTotal    당기 연간 합계
 * @param priorYearTotal 전기 연간 합계
 * @param difference     당기 연간 합계 - 전기 연간 합계
 * @param sortOrder      표시 순서
 */
public record MonthlyIncomeStatementLine(
        String rowKind,
        String section,
        String accountCode,
        String accountName,
        String category,
        List<BigDecimal> monthlyAmounts,
        BigDecimal annualTotal,
        BigDecimal priorYearTotal,
        BigDecimal difference,
        int sortOrder
) {
}
