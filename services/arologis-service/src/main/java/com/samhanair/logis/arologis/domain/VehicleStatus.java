package com.samhanair.logis.arologis.domain;

/**
 * 차량 상태 — Phase 10 W10-1.
 *
 * <ul>
 *   <li>{@link #PENDING} — 신규 등록, 매칭 대기</li>
 *   <li>{@link #MATCHING} — 외부 vendor 또는 내부 매칭 진행 중</li>
 *   <li>{@link #ASSIGNED} — 기사 배정 완료</li>
 *   <li>{@link #DEPARTED} — 출발 (첫 정차 ARRIVED 시점에 transition)</li>
 *   <li>{@link #DELIVERED} — 모든 정차 완료</li>
 *   <li>{@link #CANCELLED} — 취소</li>
 * </ul>
 */
public enum VehicleStatus {
    PENDING,
    MATCHING,
    ASSIGNED,
    DEPARTED,
    DELIVERED,
    CANCELLED
}
