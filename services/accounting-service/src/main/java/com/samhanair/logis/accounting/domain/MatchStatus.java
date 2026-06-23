package com.samhanair.logis.accounting.domain;

/** 입출금 거래 회계 반영 상태. */
public enum MatchStatus {
    /** 아직 회계 반영 전. */
    UNREFLECTED,
    /** 회계 분개로 반영 완료. */
    REFLECTED,
    /** 거래처 매칭 없이 강제 반영. */
    FORCED
}
