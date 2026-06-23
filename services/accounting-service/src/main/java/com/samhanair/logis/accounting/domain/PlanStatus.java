package com.samhanair.logis.accounting.domain;

/** 수금계획 상태. */
public enum PlanStatus {
    /** 예정. */
    PLANNED,
    /** 수금완료. */
    COLLECTED,
    /** 연체. */
    OVERDUE
}
