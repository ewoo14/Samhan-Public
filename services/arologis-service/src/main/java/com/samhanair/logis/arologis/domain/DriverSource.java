package com.samhanair.logis.arologis.domain;

/**
 * 기사 소스 — Phase 10 W10-1.
 *
 * <p>본 어플 사용 기사 vs 외부 vendor 매칭 기사 식별.
 *
 * <ul>
 *   <li>{@link #INTERNAL} — 본 어플 (RN Expo) 사용 기사</li>
 *   <li>{@link #EXTERNAL_INSUNG_QUICK} — 인성데이타 퀵프로그램 (5만 프리랜서 풀, W10-2 통합)</li>
 *   <li>{@link #EXTERNAL_SMS} — SMS 매칭 외부 기사</li>
 *   <li>{@link #EXTERNAL_KAKAO} — 카톡 매칭 외부 기사</li>
 *   <li>{@link #MANUAL} — 수동 등록 (admin 직접 입력)</li>
 * </ul>
 */
public enum DriverSource {
    INTERNAL,
    EXTERNAL_INSUNG_QUICK,
    EXTERNAL_SMS,
    EXTERNAL_KAKAO,
    MANUAL
}
