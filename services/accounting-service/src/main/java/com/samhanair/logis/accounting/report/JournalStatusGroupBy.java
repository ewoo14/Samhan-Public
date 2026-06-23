package com.samhanair.logis.accounting.report;

/** 전표현황 보고서 grouping 기준. */
public enum JournalStatusGroupBy {

    /** 일자별 그룹. */
    DATE,

    /** 출처/거래유형별 그룹. */
    SOURCE_TYPE,

    /** 거래처별 그룹. */
    PARTNER
}
