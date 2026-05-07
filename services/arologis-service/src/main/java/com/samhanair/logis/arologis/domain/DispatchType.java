package com.samhanair.logis.arologis.domain;

/**
 * 배차 유형 — Phase 10 W10-1.
 *
 * <p>카톡 헤더 텍스트 ("8일착 야상입니다" / "10일착 주간입니다") 에서 파싱.
 *
 * <ul>
 *   <li>{@link #DAY} — 주간 배송 (낮시간 출발)</li>
 *   <li>{@link #NIGHT} — 야상 (야간 상차, 익일 새벽 도착)</li>
 *   <li>{@link #EXPRESS} — 긴급 / 특급 (W10-2 시점 활용)</li>
 * </ul>
 */
public enum DispatchType {
    DAY,
    NIGHT,
    EXPRESS
}
