package com.samhanair.logis.arologis.domain;

/**
 * GPS 보고 source — Phase 10 W10-1 BE-1 / QA-3 / Designer-2 통합 채택 fix.
 *
 * <p>사용자 결정 4 (2026-05-07) — GPS 하이브리드 정책. 인성 LBS 우선 + 본 어플 GPS 보강 + 수동 fallback.
 *
 * <ul>
 *   <li>{@link #APP_GPS_BACKGROUND} — 본 어플, 백그라운드 (foreground 권한 X 시점)</li>
 *   <li>{@link #APP_GPS_ACTIVE} — 본 어플, 활성 사용 중 (foreground 권한 O)</li>
 *   <li>{@link #EXTERNAL_INSUNG_LBS} — 인성 LBS (W10-2 통합 시점 활성)</li>
 *   <li>{@link #MANUAL} — 수동 입력 fallback (admin 보정)</li>
 * </ul>
 *
 * <p>{@link DriverLocation#source} 컬럼 = VARCHAR(30) — enum 이름 그대로 string 매핑
 * ({@code @Enumerated(EnumType.STRING)}).
 */
public enum DriverLocationSource {

    /** 본 어플, 백그라운드. */
    APP_GPS_BACKGROUND,

    /** 본 어플, 활성 사용 중. */
    APP_GPS_ACTIVE,

    /** 인성 LBS (W10-2 통합 시점 활성). */
    EXTERNAL_INSUNG_LBS,

    /** 수동 입력 fallback. */
    MANUAL
}
