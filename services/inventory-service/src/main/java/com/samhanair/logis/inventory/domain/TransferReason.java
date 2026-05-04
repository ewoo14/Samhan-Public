package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이동전표 사유. SAMSUNG_DIRECT 는 가상창고-삼성 직배 시나리오 한정. */
@Getter
@RequiredArgsConstructor
public enum TransferReason {
    REBALANCE("재배치"),
    URGENT("긴급보충"),
    CONSOLIDATE("통합"),
    MAINTENANCE("점검"),
    SAMSUNG_DIRECT("삼성직배"),
    OTHER("기타");

    private final String displayName;
}
