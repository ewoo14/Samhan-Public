package com.samhanair.logis.arologis.domain;

/**
 * 매칭 소스 — Phase 10 W10-1.
 *
 * <p>차량 → 기사 매칭이 어떤 경로로 이루어졌는지 기록.
 *
 * <ul>
 *   <li>{@link #INTERNAL_APP} — 본 어플 자체 매칭 (INTERNAL Driver pool)</li>
 *   <li>{@link #EXTERNAL_INSUNG_QUICK} — 인성데이타 퀵프로그램 (W10-2)</li>
 *   <li>{@link #EXTERNAL_SMS} — SMS 매칭</li>
 *   <li>{@link #EXTERNAL_KAKAO} — 카톡 매칭</li>
 *   <li>{@link #MANUAL} — 수동 배정 (admin 직접 입력)</li>
 * </ul>
 */
public enum MatchSource {
    INTERNAL_APP,
    EXTERNAL_INSUNG_QUICK,
    EXTERNAL_SMS,
    EXTERNAL_KAKAO,
    MANUAL
}
