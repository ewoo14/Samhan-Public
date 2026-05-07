package com.samhanair.logis.dashboard.config;

import com.samhanair.logis.dashboard.service.MaterializedViewRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 *
 * <p>PR #94 W4 후속 fix (DevOps DV-W4-3) — multi-instance race 가드. {@link SchedulerLock}
 * 으로 동일 view 에 대해 여러 instance 가 동시에 REFRESH 진입하는 경로 차단.
 * single-instance 환경에서도 무해 (즉시 lock 획득 + 정상 해제). {@link ShedLockConfig} 와 V2 Flyway
 * (shedlock 테이블) 가 본 lock 의 인프라.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class MaterializedViewRefreshConfig {

    private final MaterializedViewRefreshService refreshService;

    /**
     * 5분 (= 300_000ms) 간격 REFRESH. delay 60초 — 부팅 직후 race 회피.
     *
     * <p>SpEL 변환 (PR #94 W4 종합 TM 채택 fix) — env {@code SAMHAN_DASHBOARD_REFRESH_INTERVAL}
     * (=property {@code samhan.dashboard.refresh.interval-minutes}) 가 schedule 에 반영되도록
     * application.yml + env-template + DECISIONS D-P9-13 와 key 일관성 유지.
     *
     * <p>SchedulerLock — PR #94 W4 후속 fix (DV-W4-3):
     * <ul>
     *   <li>{@code lockAtMostFor = PT10M} — instance crash 시 최대 10분 후 lock 자동 해제</li>
     *   <li>{@code lockAtLeastFor = PT4M} — REFRESH 가 매우 짧게 끝나는 경우에도 4분 동안은
     *       다른 instance 가 재진입 못 하도록 (5분 주기 직전 race 차단)</li>
     * </ul>
     */
    @Scheduled(initialDelay = 60_000L, fixedRateString = "#{${samhan.dashboard.refresh.interval-minutes:5} * 60 * 1000}")
    @SchedulerLock(name = "dashboard-mv-refresh", lockAtMostFor = "PT10M", lockAtLeastFor = "PT4M")
    public void scheduledRefresh() {
        MaterializedViewRefreshService.RefreshResult r = refreshService.refreshAll();
        log.info("Materialized view scheduled refresh — stock={} sales={}",
                r.realtimeStockOk(), r.salesDailyOk());
    }
}
