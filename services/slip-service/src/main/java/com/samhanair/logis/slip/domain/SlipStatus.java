package com.samhanair.logis.slip.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전표 상태 — 9 단계 + 분기(REJECTED/CANCELED). 출고전표는 SHIPPING/DELIVERED 단계를 거쳐
 * CONFIRMED 로 향하지만, 입고전표는 COMPLETED 에서 곧장 CONFIRMED 로 점프.
 *
 * <p>전이 규칙은 {@code Slip} 도메인 메서드 안에서 강제 (위반 시 BusinessException(CONFLICT)).
 */
@Getter
@RequiredArgsConstructor
public enum SlipStatus {
    DRAFT("작성중"),
    SAVED("저장완료"),
    SENT("전송완료"),
    ACCEPTED("수락"),
    PROCESSING("처리중"),
    COMPLETED("처리완료"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CONFIRMED("확정"),
    REJECTED("반려"),
    CANCELED("취소");

    private final String displayName;
}
