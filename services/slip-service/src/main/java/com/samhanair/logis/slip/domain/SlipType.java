package com.samhanair.logis.slip.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전표 종류 — 첫 슬라이스에서는 출고/입고 2종만 지원. 입금/출금/이동 전표는 후속 슬라이스에서 추가.
 * Single Table Inheritance 의 discriminator 역할 (Q1 결정 사항).
 */
@Getter
@RequiredArgsConstructor
public enum SlipType {
    OUTBOUND("출고전표"),
    INBOUND("입고전표");

    private final String displayName;
}
