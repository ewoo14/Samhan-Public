package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 재고 로트 상태 — FIFO 차감 후 quantity = 0 이 되면 SOLD_OUT 으로 전이. */
@Getter
@RequiredArgsConstructor
public enum StockLotStatus {
    AVAILABLE("가용"),
    SOLD_OUT("소진"),
    IN_TRANSIT("이동중");

    private final String displayName;
}
