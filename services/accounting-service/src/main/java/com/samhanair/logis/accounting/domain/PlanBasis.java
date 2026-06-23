package com.samhanair.logis.accounting.domain;

/** 수금계획 산출 근거. */
public enum PlanBasis {
    /** 외상매출금 잔액. */
    RECEIVABLE_BALANCE,
    /** 받을어음 만기. */
    NOTE_MATURITY,
    /** 수동 입력. */
    MANUAL
}
