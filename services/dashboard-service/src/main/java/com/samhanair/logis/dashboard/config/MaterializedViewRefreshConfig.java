package com.samhanair.logis.dashboard.config;

import com.samhanair.logis.dashboard.service.MaterializedViewRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Materialized view scheduled REFRESH config — Phase 9 W4 (D-P9-13).
 *
 * <p>{@code samhan.dashboard.refresh.interval-minutes} (default 5) 주기로 양쪽 view REFRESH.
 * fixed-rate 모드 (이전 주기 종료 무관 — REFRESH 가 5분 초과 시 큐 누적 회피, fail-soft 로 skip).
 *
 * <p>주기 테스트 격리 — IT 에서는 본 config 가 active 이지만 {@code @Scheduled} 첫 호출 전에
 * IT 가 끝나거나 영향이 미미. 단위 테스트는 본 config 없이 service 직접 호출.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class MaterializedViewRefreshConfig {

    private final MaterializedViewRefreshService refreshService;

    /**
     * 5분 (= 300_000ms) 간격 REFRESH. delay 60초 — 부팅 직후 race 회피.
     */
    @Scheduled(initialDelay = 60_000L, fixedRateString = "${samhan.dashboard.refresh.interval-millis:300000}")
    public void scheduledRefresh() {
        MaterializedViewRefreshService.RefreshResult r = refreshService.refreshAll();
        log.info("Materialized view scheduled refresh — stock={} sales={}",
                r.realtimeStockOk(), r.salesDailyOk());
    }
}
