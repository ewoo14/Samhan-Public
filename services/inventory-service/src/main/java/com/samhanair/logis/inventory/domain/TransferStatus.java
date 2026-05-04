package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동전표 상태 머신.
 * REQUESTED → PENDING_APPROVAL → APPROVED → SHIPPED → IN_TRANSIT → RECEIVED → CONFIRMED.
 * REJECTED / CANCELED 는 종착 상태. 가상창고가 source 또는 destination 이면
 * SHIPPED → IN_TRANSIT 단계 스킵하고 즉시 RECEIVED 까지 자동 진행.
 */
@Getter
@RequiredArgsConstructor
public enum TransferStatus {
    REQUESTED("요청됨"),
    PENDING_APPROVAL("결재대기"),
    APPROVED("승인"),
    SHIPPED("출고"),
    IN_TRANSIT("이동중"),
    RECEIVED("입고"),
    CONFIRMED("확정"),
    REJECTED("반려"),
    CANCELED("취소");

    private final String displayName;
}
