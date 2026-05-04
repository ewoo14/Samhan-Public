package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 재고 이동 유형 — append-only 감사 로그 (StockMovement) 의 분류 컬럼. */
@Getter
@RequiredArgsConstructor
public enum MovementType {
    INBOUND("입고"),
    RESERVE("예약"),
    RELEASE("예약해제"),
    DEDUCT("차감"),
    TRANSFER_OUT("이동출고"),
    TRANSFER_IN("이동입고"),
    ADJUST("조정");

    private final String displayName;
}
