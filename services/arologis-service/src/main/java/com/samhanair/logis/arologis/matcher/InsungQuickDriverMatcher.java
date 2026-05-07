package com.samhanair.logis.arologis.matcher;

import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStop;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 인성데이타 퀵프로그램 DriverMatcher placeholder — Phase 10 W10-2 통합 시점 활성.
 *
 * <p>본 PR (W10-1) 은 placeholder — 호출 시 {@link UnsupportedOperationException} throw.
 *
 * <p>{@code samhan.arologis.matcher.provider=insung-quick} 시 활성 ({@link com.samhanair.logis.arologis.config.MatcherConfig}
 * 가 conditional bean 등록). W10-2 시점 실 vendor API 호출 + 응답 파싱 + Driver upsert 구현 의무.
 *
 * <p>구현 후보 (W10-2):
 * <ul>
 *   <li>POST {@code /api/orders} — 차량 + 정차 정보 전송 → orderId 응답</li>
 *   <li>POST {@code /api/orders/{orderId}/match} — 5만 프리랜서 풀에서 매칭 trigger</li>
 *   <li>callback 수신 — {@code POST /internal/arologis/dispatches/sync} (본 PR Internal endpoint)</li>
 * </ul>
 */
@Slf4j
@Component
public class InsungQuickDriverMatcher implements DriverMatcher {

    @Override
    public DriverMatchResult match(Vehicle vehicle, List<VehicleStop> stops) {
        log.warn("InsungQuickDriverMatcher.match 호출 — W10-1 단계는 placeholder. "
                + "W10-2 인성데이타 vendor 통합 시점에 활성됩니다 (D-P10-03).");
        throw new UnsupportedOperationException(
                "W10-2 인성데이타 vendor 통합 시점 활성 — 본 PR (W10-1) 은 placeholder");
    }

    @Override
    public MatchSource source() {
        return MatchSource.EXTERNAL_INSUNG_QUICK;
    }
}
