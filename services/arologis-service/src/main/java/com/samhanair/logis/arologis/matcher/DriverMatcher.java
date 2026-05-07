package com.samhanair.logis.arologis.matcher;

import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStop;
import java.util.List;

/**
 * 기사 매칭 추상화 — Phase 10 W10-1.
 *
 * <p>vendor 교체 가능 design — Mock (W10-1 default) / Insung Quick (W10-2 통합) / SMS (W10-2+) /
 * Kakao (W10-2+). {@code samhan.arologis.matcher.provider} property 로 단일 활성 vendor 선택.
 *
 * <p>구현 의무:
 * <ul>
 *   <li>{@link #match(Vehicle, List)} — 차량 + 정차 → 기사 매칭. 실패 시 {@link DriverMatchResult#empty(MatchSource)}</li>
 *   <li>{@link #source()} — 본 vendor 의 source enum (MatchSource)</li>
 * </ul>
 *
 * <p>fail-soft 정책 — 외부 RPC 예외 시 empty 반환 (admin 수동 매칭 fallback). 호출 측은 empty 인 경우
 * Vehicle.status 를 PENDING 유지 + 다음 vendor 시도 (현재 단일 vendor pattern 이지만 W10-2 시점 확장 의무).
 */
public interface DriverMatcher {

    /**
     * 차량 + 정차 목록 → 기사 매칭 시도.
     *
     * @param vehicle 매칭 대상 차량
     * @param stops 차량의 정차 목록 (라우팅 hint)
     * @return 매칭 결과 (성공 시 driver / source / externalRefId, 실패 시 empty)
     */
    DriverMatchResult match(Vehicle vehicle, List<VehicleStop> stops);

    /** 본 vendor 의 source enum. */
    MatchSource source();
}
