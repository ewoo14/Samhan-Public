package com.samhanair.logis.accounting.domain;

/** 받을어음 상태. */
public enum NoteStatus {
    /** 보유. */
    BOARDING,
    /** 추심. */
    COLLECTING,
    /** 결제완료. */
    SETTLED,
    /** 부도. */
    DISHONORED
}
