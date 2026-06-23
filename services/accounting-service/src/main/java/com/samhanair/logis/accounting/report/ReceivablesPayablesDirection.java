package com.samhanair.logis.accounting.report;

/** 채권채무 현황 조회 방향. */
public enum ReceivablesPayablesDirection {
    /** 채권 계정(외상매출금/미수금)만 조회한다. */
    RECEIVABLE,
    /** 채무 계정(외상매입금/미지급금)만 조회한다. */
    PAYABLE,
    /** 채권과 채무를 한 화면에서 동시에 조회한다. */
    ALL
}
