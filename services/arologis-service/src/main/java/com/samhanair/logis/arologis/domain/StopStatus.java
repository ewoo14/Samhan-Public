package com.samhanair.logis.arologis.domain;

/**
 * 정차 상태 — Phase 10 W10-1.
 *
 * <ul>
 *   <li>{@link #PENDING} — 신규 등록, 도착 대기</li>
 *   <li>{@link #ARRIVED} — 기사 도착 (어플 또는 admin 수동 입력)</li>
 *   <li>{@link #DELIVERED} — 인수 완료 + 전자서명 보유</li>
 *   <li>{@link #FAILED} — 배송 실패 (부재 / 거부 등)</li>
 *   <li>{@link #UNPARSED} — 카톡 파싱 단계 미해석 라인 (예: "상일상차" 그룹 라벨)</li>
 * </ul>
 */
public enum StopStatus {
    PENDING,
    ARRIVED,
    DELIVERED,
    FAILED,
    UNPARSED
}
