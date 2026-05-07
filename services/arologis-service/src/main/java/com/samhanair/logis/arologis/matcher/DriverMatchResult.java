package com.samhanair.logis.arologis.matcher;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.MatchSource;
import java.util.Optional;

/**
 * 기사 매칭 결과 — Phase 10 W10-1.
 *
 * @param driver 매칭된 기사 (없으면 empty)
 * @param source 매칭 경로
 * @param externalRefId 외부 vendor 주문번호 (옵션)
 */
public record DriverMatchResult(
        Optional<Driver> driver,
        MatchSource source,
        String externalRefId
) {

    /** 매칭 실패 결과 (driver = empty). */
    public static DriverMatchResult empty(MatchSource source) {
        return new DriverMatchResult(Optional.empty(), source, null);
    }

    /** 매칭 성공 결과. */
    public static DriverMatchResult of(Driver driver, MatchSource source, String externalRefId) {
        return new DriverMatchResult(Optional.of(driver), source, externalRefId);
    }
}
