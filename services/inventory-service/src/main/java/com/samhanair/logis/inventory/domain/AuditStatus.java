package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 실사 상태 머신 (Phase 10 P2-6).
 *
 * <p>전이 규칙:
 * <ul>
 *   <li>PLANNED → IN_PROGRESS (start)</li>
 *   <li>IN_PROGRESS → COMPLETED (complete — 차이 자동 분개 + Stock 조정 trigger)</li>
 *   <li>PLANNED / IN_PROGRESS → CANCELLED (cancel)</li>
 * </ul>
 * COMPLETED / CANCELLED 는 종착.
 */
@Getter
@RequiredArgsConstructor
public enum AuditStatus {
    PLANNED("계획됨"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    CANCELLED("취소");

    private final String displayName;
}
