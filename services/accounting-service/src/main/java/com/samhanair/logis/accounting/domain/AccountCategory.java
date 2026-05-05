package com.samhanair.logis.accounting.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한국 일반기업회계기준 표준 계정과목 7-그룹 (Plan §3 + 메모리 project_korean_accounting.md).
 * 코드 prefix 1자리는 그룹 식별 (1=ASSET, 2=LIABILITY, 3=EQUITY, 4=REVENUE, 5=COST_OF_SALES,
 * 8=SGA, 9=NON_OPERATING/INCOME_TAX). 600/700 영역은 향후 확장 (제조 원가).
 */
@Getter
@RequiredArgsConstructor
public enum AccountCategory {

    ASSET("자산", "100"),
    LIABILITY("부채", "200"),
    EQUITY("자본", "300"),
    REVENUE("매출", "400"),
    COST_OF_SALES("매출원가", "500"),
    SGA("판매비와관리비", "800"),
    NON_OPERATING("영업외손익", "900"),
    INCOME_TAX("법인세비용", "990");

    private final String displayName;
    private final String codePrefix;
}
